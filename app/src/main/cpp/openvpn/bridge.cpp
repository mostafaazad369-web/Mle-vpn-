// SPDX-License-Identifier: GPL-3.0-or-later
// MLE adaptation of the OpenVPN 3 JNI integration audited from mlmvpn/mlmvpn_android.
// Upstream credit: https://github.com/mlmvpn/mlmvpn_android

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>

#include <openvpn/io/io.hpp>
#include <client/ovpncli.hpp>
#include <openvpn/client/dns_options.hpp>

namespace api = openvpn::ClientAPI;
namespace {

constexpr const char* TAG = "MleOpenVpnCore";

std::string toUtf8(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) throw std::runtime_error("JNI string conversion failed");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

/** Obtains a thread-local JNIEnv; never caches JNIEnv across native threads. */
class ScopedEnv final {
public:
    explicit ScopedEnv(JavaVM* vm) : vm_(vm) {
        if (!vm_) return;
        const jint state = vm_->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
        if (state == JNI_EDETACHED) {
            if (vm_->AttachCurrentThread(&env_, nullptr) == JNI_OK) attached_ = true;
            else env_ = nullptr;
        } else if (state != JNI_OK) {
            env_ = nullptr;
        }
    }
    ~ScopedEnv() {
        if (attached_ && vm_) vm_->DetachCurrentThread();
    }
    JNIEnv* get() const { return env_; }
    explicit operator bool() const { return env_ != nullptr; }

private:
    JavaVM* vm_ = nullptr;
    JNIEnv* env_ = nullptr;
    bool attached_ = false;
};

struct CallbackMethods {
    jmethodID configureTun = nullptr;
    jmethodID addAddress = nullptr;
    jmethodID addRoute = nullptr;
    jmethodID addDns = nullptr;
    jmethodID addSearchDomain = nullptr;
    jmethodID establishTun = nullptr;
    jmethodID onCoreEvent = nullptr;
    jmethodID onCoreTraffic = nullptr;
};

class Client final : public api::OpenVPNClient {
public:
    std::atomic<bool> cancelled{false};
    std::atomic<bool> running{false};

    bool installCallback(JNIEnv* env, jobject callback) {
        if (!env || !callback) return false;
        jclass cls = env->GetObjectClass(callback);
        if (!cls) return false;

        CallbackMethods methods;
        methods.configureTun = env->GetMethodID(cls, "configureTun", "(Ljava/lang/String;I)Z");
        methods.addAddress = env->GetMethodID(cls, "addAddress", "(Ljava/lang/String;I)Z");
        methods.addRoute = env->GetMethodID(cls, "addRoute", "(Ljava/lang/String;I)Z");
        methods.addDns = env->GetMethodID(cls, "addDns", "(Ljava/lang/String;I)Z");
        methods.addSearchDomain = env->GetMethodID(cls, "addSearchDomain", "(Ljava/lang/String;I)Z");
        methods.establishTun = env->GetMethodID(cls, "establishTun", "()I");
        methods.onCoreEvent = env->GetMethodID(cls, "onCoreEvent", "(Ljava/lang/String;Z)V");
        methods.onCoreTraffic = env->GetMethodID(cls, "onCoreTraffic", "(JJ)V");
        env->DeleteLocalRef(cls);

        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        if (!methods.configureTun || !methods.addAddress || !methods.addRoute ||
            !methods.addDns || !methods.addSearchDomain || !methods.establishTun ||
            !methods.onCoreEvent || !methods.onCoreTraffic) return false;

        jobject global = env->NewGlobalRef(callback);
        if (!global) return false;
        std::lock_guard<std::mutex> lock(callbackMutex_);
        callback_ = global;
        methods_ = methods;
        return true;
    }

    void clearCallback(JNIEnv* env) {
        jobject old = nullptr;
        {
            std::lock_guard<std::mutex> lock(callbackMutex_);
            old = callback_;
            callback_ = nullptr;
            methods_ = {};
        }
        if (env && old) env->DeleteGlobalRef(old);
    }

    bool callBoolean(jmethodID method, const std::string& value, int number = 0) {
        ScopedEnv scoped(vm_);
        if (!scoped) return false;
        JNIEnv* env = scoped.get();
        jobject callback = nullptr;
        {
            std::lock_guard<std::mutex> lock(callbackMutex_);
            if (!callback_ || !method) return false;
            callback = env->NewLocalRef(callback_);
        }
        if (!callback) return false;
        jstring text = env->NewStringUTF(value.c_str());
        if (!text) {
            env->DeleteLocalRef(callback);
            return false;
        }
        const jboolean result = env->CallBooleanMethod(callback, method, text, number);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(callback);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cancelled.store(true);
            stop();
            return false;
        }
        return result == JNI_TRUE;
    }

    void sendEvent(const std::string& name, bool error) {
        ScopedEnv scoped(vm_);
        if (!scoped) return;
        JNIEnv* env = scoped.get();
        jobject callback = nullptr;
        jmethodID method = nullptr;
        {
            std::lock_guard<std::mutex> lock(callbackMutex_);
            if (!callback_ || !methods_.onCoreEvent) return;
            callback = env->NewLocalRef(callback_);
            method = methods_.onCoreEvent;
        }
        if (!callback) return;
        jstring text = env->NewStringUTF(name.c_str());
        if (text) {
            env->CallVoidMethod(callback, method, text, error ? JNI_TRUE : JNI_FALSE);
            env->DeleteLocalRef(text);
        }
        env->DeleteLocalRef(callback);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cancelled.store(true);
            stop();
        }
    }

    bool tun_builder_new() override {
        return callBoolean(methodSnapshot().configureTun, "new");
    }

    bool tun_builder_add_address(const std::string& address, int prefix,
                                 const std::string&, bool, bool) override {
        return callBoolean(methodSnapshot().addAddress, address, prefix);
    }

    bool tun_builder_add_route(const std::string& address, int prefix, int, bool) override {
        return callBoolean(methodSnapshot().addRoute, address, prefix);
    }

    bool tun_builder_reroute_gw(bool v4, bool v6, unsigned int) override {
        return (!v4 || callBoolean(methodSnapshot().addRoute, "0.0.0.0", 0)) &&
               (!v6 || callBoolean(methodSnapshot().addRoute, "::", 0));
    }

    bool tun_builder_set_remote_address(const std::string&, bool) override { return true; }

    bool tun_builder_set_mtu(int mtu) override {
        return callBoolean(methodSnapshot().configureTun, "mtu", mtu);
    }

    bool tun_builder_set_session_name(const std::string&) override { return true; }

    bool tun_builder_set_dns_options(const openvpn::DnsOptions& dns) override {
        for (const auto& [priority, server] : dns.servers) {
            (void)priority;
            for (const auto& address : server.addresses) {
                if (address.port && address.port != 53) return false;
                if (!callBoolean(methodSnapshot().addDns, address.address)) return false;
            }
        }
        for (const auto& domain : dns.search_domains) {
            if (!callBoolean(methodSnapshot().addSearchDomain, domain.domain)) return false;
        }
        return true;
    }

    int tun_builder_establish() override {
        if (cancelled.load()) return -1;
        ScopedEnv scoped(vm_);
        if (!scoped) return -1;
        JNIEnv* env = scoped.get();
        jobject callback = nullptr;
        jmethodID method = nullptr;
        {
            std::lock_guard<std::mutex> lock(callbackMutex_);
            if (!callback_ || !methods_.establishTun) return -1;
            callback = env->NewLocalRef(callback_);
            method = methods_.establishTun;
        }
        if (!callback) return -1;
        const jint fd = env->CallIntMethod(callback, method);
        env->DeleteLocalRef(callback);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cancelled.store(true);
            return -1;
        }
        return fd;
    }

    bool socket_protect(openvpn_io::detail::socket_type fd, std::string, bool) override {
        return callBoolean(methodSnapshot().configureTun, "protect", static_cast<int>(fd));
    }

    bool pause_on_connection_timeout() override { return false; }
    void event(const api::Event& event) override { sendEvent(event.name, event.error || event.fatal); }
    void acc_event(const api::AppCustomControlMessageEvent&) override {}

    void log(const api::LogInfo& info) override {
        // Do not forward core logs to Android logcat: profile and server messages
        // are not guaranteed to be free of user-controlled secrets.
        (void)info;
    }

    void external_pki_cert_request(api::ExternalPKICertRequest& request) override {
        request.error = true;
    }
    void external_pki_sign_request(api::ExternalPKISignRequest& request) override {
        request.error = true;
    }
    void connect_pre_run() override {
        if (cancelled.load()) stop();
    }

    void clock_tick() override {
        if (cancelled.load()) {
            stop();
            return;
        }
        ScopedEnv scoped(vm_);
        if (!scoped) return;
        JNIEnv* env = scoped.get();
        jobject callback = nullptr;
        jmethodID method = nullptr;
        {
            std::lock_guard<std::mutex> lock(callbackMutex_);
            if (!callback_ || !methods_.onCoreTraffic) return;
            callback = env->NewLocalRef(callback_);
            method = methods_.onCoreTraffic;
        }
        if (!callback) return;
        const auto stats = tun_stats();
        env->CallVoidMethod(callback, method,
            static_cast<jlong>(stats.bytesOut), static_cast<jlong>(stats.bytesIn));
        env->DeleteLocalRef(callback);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            cancelled.store(true);
            stop();
        }
    }

    bool initializeVm(JavaVM* vm) {
        vm_ = vm;
        return vm_ != nullptr;
    }

private:
    CallbackMethods methodSnapshot() {
        std::lock_guard<std::mutex> lock(callbackMutex_);
        return methods_;
    }

    JavaVM* vm_ = nullptr;
    std::mutex callbackMutex_;
    jobject callback_ = nullptr; // global reference; deleted after connect returns
    CallbackMethods methods_{};
};

std::mutex clientsMutex;
std::unordered_map<jlong, std::shared_ptr<Client>> clients;
std::atomic<jlong> nextId{1};

std::shared_ptr<Client> findClient(jlong id) {
    std::lock_guard<std::mutex> lock(clientsMutex);
    const auto it = clients.find(id);
    return it == clients.end() ? nullptr : it->second;
}

api::Config configuration(std::string content) {
    api::Config config;
    config.content = std::move(content);
    config.guiVersion = "MLE VPN";
    config.connTimeout = 40;
    config.clockTickMS = 1000;
    config.tunPersist = false;
    config.retryOnAuthFailed = false;
    // Keep OpenVPN's safe default compression policy. Do not inherit provider-
    // specific asymmetric compression from the upstream MLM integration.
    config.disableClientCert = config.content.find("<cert>") == std::string::npos;
    return config;
}

} // namespace

#define MLE_JNI(name) Java_com_mle_vpn_engine_openvpn_MleOpenVpnNative_##name

extern "C" JNIEXPORT jint JNICALL MLE_JNI(apiVersion)(JNIEnv*, jobject) {
    return 1;
}

extern "C" JNIEXPORT jstring JNICALL MLE_JNI(evaluate)(JNIEnv* env, jobject, jstring text) {
    try {
        Client client;
        auto result = client.eval_config(configuration(toUtf8(env, text)));
        return env->NewStringUTF(result.error ? "PROFILE_UNSUPPORTED" : "");
    } catch (...) {
        return env->NewStringUTF("PROFILE_INVALID");
    }
}

extern "C" JNIEXPORT jlong JNICALL MLE_JNI(create)(JNIEnv* env, jobject) {
    try {
        JavaVM* vm = nullptr;
        if (env->GetJavaVM(&vm) != JNI_OK || !vm) return 0;
        auto client = std::make_shared<Client>();
        if (!client->initializeVm(vm)) return 0;
        const jlong id = nextId.fetch_add(1);
        std::lock_guard<std::mutex> lock(clientsMutex);
        clients.emplace(id, std::move(client));
        return id;
    } catch (...) {
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL MLE_JNI(stop)(JNIEnv*, jobject, jlong id) {
    if (auto client = findClient(id)) {
        client->cancelled.store(true);
        client->stop();
    }
}

extern "C" JNIEXPORT void JNICALL MLE_JNI(release)(JNIEnv*, jobject, jlong id) {
    std::shared_ptr<Client> client;
    {
        std::lock_guard<std::mutex> lock(clientsMutex);
        const auto it = clients.find(id);
        if (it == clients.end()) return;
        client = std::move(it->second);
        clients.erase(it);
    }
    client->cancelled.store(true);
    client->stop();
}

extern "C" JNIEXPORT jstring JNICALL MLE_JNI(run)(
    JNIEnv* env, jobject, jlong id, jstring text, jstring username, jstring password, jobject callback) {
    auto client = findClient(id);
    if (!client) return env->NewStringUTF("CORE_UNAVAILABLE");

    bool expected = false;
    if (!client->running.compare_exchange_strong(expected, true)) {
        return env->NewStringUTF("CORE_BUSY");
    }

    std::string error;
    try {
        if (!client->installCallback(env, callback)) {
            error = "CALLBACK_UNAVAILABLE";
        } else if (!client->cancelled.load()) {
            auto config = configuration(toUtf8(env, text));
            auto evaluation = client->eval_config(config);
            if (evaluation.error) {
                error = "PROFILE_UNSUPPORTED";
            } else if (!client->cancelled.load()) {
                api::ProvideCreds credentials;
                credentials.username = toUtf8(env, username);
                credentials.password = toUtf8(env, password);
                auto provided = client->provide_creds(credentials);
                if (provided.error) {
                    error = "CREDENTIALS_REJECTED";
                } else if (!client->cancelled.load()) {
                    auto result = client->connect();
                    if (result.error && !client->cancelled.load()) error = "CONNECTION_FAILED";
                }
            }
        }
    } catch (...) {
        error = "CORE_ERROR";
    }

    client->clearCallback(env);
    client->running.store(false);
    return env->NewStringUTF(error.c_str());
}
