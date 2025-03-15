#include <android/log.h>
#include <utility>
#include "iperf-jni.h"

#define OUTPUT_LENGTH  10240
#include <csignal>
#include <ctime>

volatile sig_atomic_t stop_server = 0;

iPerfNative *iPerfNative::_instance = nullptr;

static const char role = 'c';

int printf(const char *fmt, ...) {
    va_list argptr;
    int cnt;
    va_start(argptr, fmt);
    char *buffer = (char *) malloc(OUTPUT_LENGTH);
    memset(buffer, OUTPUT_LENGTH, 0);
    cnt = vsnprintf(buffer, OUTPUT_LENGTH, fmt, argptr);
    buffer[cnt] = '\0';
    if (iPerfNative::instance().isDebug()) {
        logi("ifref message(printf): %s", buffer);
    }
    iPerfNative::instance().onAppendResult(buffer);
    free(buffer);
    va_end(argptr);
    return cnt;
}

int fprintf(FILE *fp, const char *fmt, ...) {
    va_list argptr;
    int cnt;
    va_start(argptr, fmt);
    char *buffer = (char *) malloc(OUTPUT_LENGTH);
    memset(buffer, OUTPUT_LENGTH, 0);
    cnt = vsnprintf(buffer, OUTPUT_LENGTH, fmt, argptr);
    buffer[cnt] = '\0';
    if (iPerfNative::instance().isDebug()) {
        logi("ifref message(fprintf): %s", buffer);
    }
    iPerfNative::instance().onAppendResult(buffer);
    free(buffer);
    va_end(argptr);
    return cnt;
}

int vfprintf(FILE *fp, const char *fmt, va_list args) {
    int cnt;
    char *buffer = (char *) malloc(OUTPUT_LENGTH);
    memset(buffer, OUTPUT_LENGTH, 0);
    cnt = vsnprintf(buffer, OUTPUT_LENGTH, fmt, args);
    buffer[cnt] = '\0';
    if (iPerfNative::instance().isDebug()) {
        logi("ifref message(vfprintf): %s", buffer);
    }
    iPerfNative::instance().onAppendResult(buffer);
    free(buffer);
    return cnt;
}

void perror(const char *msg) {
    loge("ifref error message(perror): %s", msg);
}

iPerfNative::iPerfNative() {
    if (_instance != nullptr) {
        throw "Instance already exists";
    }
    _instance = this;
    this->deInit();
}


iPerfNative::~iPerfNative() {
    this->deInit();
    _instance = nullptr;
}

iPerfNative &iPerfNative::instance() {
    if (_instance == nullptr) {
        throw "Instance does not exists";
    }
    return *_instance;
}

void iPerfNative::deInit() {
    if (this->test != nullptr) {
        iperf_free_test(this->test);
    }
}

// Custom callback to print JSON results every second
int iPerfNative::json_callback(struct iperf_test *test) {
    if (test->json_output_string) {
        std::cout << "JSON Output Every Second:\n" << test->json_output_string << "\n";
    }
    return 0;
}

void iPerfNative::init(char *hostname, int port, char *streamTemplate, int duration,
                       int interval, bool download, bool useUDP, bool json, char role) {
    this->hostname = hostname;
    this->port = port;
    this->streamTemplate = streamTemplate;
    this->duration = duration;
    this->interval = interval;
    this->download = download;
    this->useUDP = useUDP;
    this->json = json;
    this->role = role;

    this->test = iperf_new_test();

    // load defaults
    iperf_defaults(this->test);

    iperf_set_test_json_output(test, this->json ? 1 : 0);

    // need this to get the output in local printf
    iperf_set_verbose(test, 1);

    // set role as client
    iperf_set_test_role(this->test, role);
    if(this->test->role == 's') {
        iperf_set_test_bind_address(test, "0.0.0.0"); // Listen on all interfaces
    } else {
       // iperf_set_test_json_callback(this->test, json_callback); // Attach the JSON callback

        test->settings->unit_format = 'b';

    }


    // set download-> 1/ upload-> 0
    iperf_set_test_reverse(this->test, download ? 1 : 0);

    iperf_set_test_server_hostname(this->test, (char *) this->hostname.c_str());
    iperf_set_test_server_port(this->test, this->port);

    iperf_set_test_duration(this->test, this->duration);
    iperf_set_test_reporter_interval(this->test, this->interval);
    iperf_set_test_stats_interval(this->test, 1);

    iperf_set_test_template(this->test, (char *) this->streamTemplate.c_str());
    iperf_set_test_num_streams(this->test, 1);
    iperf_set_test_blksize(this->test, 4096);

    if(!useUDP) {
        set_protocol(this->test, Ptcp);
    }else{
        set_protocol(this->test, Pudp);
        test->settings->blksize = 0;
    }

    // optionals: only when debug is on
    test->get_server_output = this->debug ? 1 : 0;
    this->test->debug = this->debug ? 1 : 0;
}



static void timer_expired(int sig) {
    //std::cout << "\nTimer expired. Stopping iPerf3 server...\n";
    stop_server = 1;  // Set the flag to stop the server loop
}


// Function to start a countdown timer
static void start_timer(int seconds) {
    struct sigevent sev = {};
    struct itimerspec its = {};
    timer_t timer_id;

    sev.sigev_notify = SIGEV_SIGNAL;
    sev.sigev_signo = SIGALRM;  // Trigger SIGALRM when timer expires
    sev.sigev_value.sival_ptr = &timer_id;
    signal(SIGALRM, timer_expired);  // Set signal handler

    if (timer_create(CLOCK_REALTIME, &sev, &timer_id) < 0) {
        perror("timer_create failed");
        return;
    }

    its.it_value.tv_sec = seconds;  // Set duration (seconds)
    its.it_value.tv_nsec = 0;
    its.it_interval.tv_sec = 0;  // Run only once
    its.it_interval.tv_nsec = 0;

    if (timer_settime(timer_id, 0, &its, NULL) < 0) {
        perror("timer_settime failed");
    }
}

static int run(struct iperf_test *test) {
    int ret = 0;
    if(test->role == 's') {


        // Set timeout to stop the server after 60 seconds
        unsigned int duration = 2;

        start_timer(duration);
        while (!stop_server) {
            iperf_reset_test(test);
            iperf_set_test_role(test, 's');
            iperf_set_test_server_port(test, 5201);
            ret = iperf_run_server(test);

        }
        stop_server = 0;

    } else {
        ret = iperf_run_client(test);
    }


    logd("iperf_run_client() result: %d", ret);
    return ret;
}

int iPerfNative::execute() {
    onClearResult();
    if(json){
        onAppendResult("Accumulating results in JSON format...\n");
    }
    auto run_future = std::async(std::launch::async, run, this->test);
    auto result = run_future.get();
    logd("finish iperf request, status: %d", result);
    return result;
}

const char *iPerfNative::getHostname() {
    return hostname.c_str();
}

void iPerfNative::setHostname(char *hostname) {
    iPerfNative::hostname = std::string(hostname);
}

int iPerfNative::getPort() const {
    return port;
}

void iPerfNative::setPort(int port) {
    iPerfNative::port = port;
}

const char *iPerfNative::getStreamTemplate() {
    return streamTemplate.c_str();
}

void iPerfNative::setStreamTemplate(char *streamTemplate) {
    iPerfNative::streamTemplate = std::string(streamTemplate);
}

int iPerfNative::getDuration() const {
    return download;
}

void iPerfNative::setDuration(int duration) {
    iPerfNative::download = duration;
}

int iPerfNative::getInterval() const {
    return interval;
}

void iPerfNative::setInterval(int interval) {
    iPerfNative::interval = interval;
}

bool iPerfNative::isDebug() const {
    return debug;
}

void iPerfNative::setDebug(bool debug) {
    iPerfNative::debug = debug;
}

bool iPerfNative::isDownload() const {
    return download;
}

void iPerfNative::setDownload(bool download) {
    iPerfNative::download = download;
}

bool iPerfNative::isJson() const {
    return json;
}

void iPerfNative::setJson(bool json) {
    iPerfNative::json = json;
}

bool iPerfNative::isUseUDP() const {
    return useUDP;
}

void iPerfNative::setUseUDP(bool udp) {
    iPerfNative::useUDP = udp;
}

iPerfNativeCallbacks::~iPerfNativeCallbacks() = default;
