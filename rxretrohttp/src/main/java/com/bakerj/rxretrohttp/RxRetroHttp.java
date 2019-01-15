package com.bakerj.rxretrohttp;

import android.annotation.SuppressLint;
import android.app.Application;

import com.bakerj.rxretrohttp.client.BaseRetroClient;
import com.bakerj.rxretrohttp.client.SimpleRetroClient;
import com.bakerj.rxretrohttp.https.HttpsUtils;
import com.bakerj.rxretrohttp.interceptors.DateFixInterceptor;
import com.bakerj.rxretrohttp.interfaces.IBaseApiAction;
import com.facebook.stetho.Stetho;
import com.trello.rxlifecycle2.LifecycleTransformer;

import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

/**
 * Created by BakerJ on 2019/1/15
 * Rx+Retrofit
 */
public class RxRetroHttp {

    private static final int DEFAULT_TIMEOUT = 10000;//default timeout 默认3chaoshi
    private static final int DEFAULT_RETRY_COUNT = 3;//default retry count 默认重试
    private static final int DEFAULT_RETRY_INCREASE_DELAY = 0;//default retry increase 默认重试延时增量
    private static final int DEFAULT_RETRY_DELAY = 500;//default retry delay 默认重试延时
    @SuppressLint("StaticFieldLeak")
    private static Application sApplication;
    @SuppressLint("StaticFieldLeak")
    private static volatile RxRetroHttp INSTANCE;
    private String mBaseUrl;//api base url 请求地址
    private int mTimeOut = DEFAULT_TIMEOUT;//time out 超时
    private int mRetryCount = DEFAULT_RETRY_COUNT;//retry count 重试次数
    private int mRetryDelay = DEFAULT_RETRY_DELAY;//retry delay 重试延时
    private int mRetryIncreaseDelay = DEFAULT_RETRY_INCREASE_DELAY;//retry increase 重试延时增量
    private OkHttpClient.Builder mOkHttpClientBuilder;//okhttp builder
    private Retrofit.Builder mRetrofitBuilder;//retrofit builder
    private BaseRetroClient mCommonRetroClient;//retro client
    //retro client tag pair, which is used for dealing multi-apiResult and multi-baseUrl
    //tag-请求Client对，用于处理多返回结果和多url的情况
    private Map<String, BaseRetroClient> mRetroClientMap = new HashMap<>();
    private boolean mIsDebug = false;//isDebug
    private Class apiResultClass;//api result class 返回结果对应的类
    private String defaultErrorMsg = "";

    private RxRetroHttp() {
        generateOkHttpBuilder();
        generateRetrofitBuilder();
    }

    public static RxRetroHttp getInstance() {
        if (INSTANCE == null) {
            synchronized (RxRetroHttp.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RxRetroHttp();
                }
            }
        }
        return INSTANCE;
    }

    public static OkHttpClient getOkHttpClient() {
        return getInstance().mOkHttpClientBuilder.build();
    }

    public static Retrofit getRetrofit() {
        return getInstance().mRetrofitBuilder.build();
    }

    public static OkHttpClient.Builder getOkHttpClientBuilder() {
        return getInstance().mOkHttpClientBuilder;
    }

    public static Retrofit.Builder getRetrofitBuilder() {
        return getInstance().mRetrofitBuilder;
    }

    public static String getBaseUrl() {
        return getInstance().mBaseUrl;
    }

    public RxRetroHttp setBaseUrl(String baseUrl) {
        this.mBaseUrl = baseUrl;
        mOkHttpClientBuilder.hostnameVerifier(new UnSafeHostnameVerifier(mBaseUrl));
        mRetrofitBuilder.baseUrl(mBaseUrl);
        return this;
    }

    public static Class getApiResultClass() {
        return getInstance().apiResultClass;
    }

    public RxRetroHttp setApiResultClass(Class apiResultClass) {
        this.apiResultClass = apiResultClass;
        return this;
    }

    public static String getDefaultErrMsg() {
        return getInstance().defaultErrorMsg;
    }

    public RxRetroHttp setDefaultErrMsg(String errMsg) {
        this.defaultErrorMsg = errMsg;
        return this;
    }

    public static int getRetryCount() {
        return getInstance().mRetryCount;
    }

    public static int getRetryDelay() {
        return getInstance().mRetryDelay;
    }

    public static int getRetryIncreaseDelay() {
        return getInstance().mRetryIncreaseDelay;
    }

    public static boolean isDebug() {
        return getInstance().mIsDebug;
    }

    public RxRetroHttp setDebug(boolean isDebug) {
        mIsDebug = isDebug;
        return this;
    }

    public static <T> T create(Class<T> cls) {
        return (T) getInstance().mCommonRetroClient.create(cls);
    }

    /**
     * create apiService Interface by tag, deal with multi-url
     * 根据tag创建api服务
     */
    public static <T> T create(Class<T> cls, String tag) {
        BaseRetroClient retroClient = getInstance().getRetroClient(tag);
        if (retroClient == null) {
            return null;
        }
        return (T) retroClient.create(cls);
    }

    private static <T> ObservableTransformer<T, T> composeApi() {
        return getInstance().mCommonRetroClient.composeApi();
    }

    private static <T> ObservableTransformer<T, T> composeApi(String tag) {
        BaseRetroClient retroClient = getInstance().getRetroClient(tag);
        if (retroClient == null) {
            return composeApi();
        }
        return retroClient.composeApi();
    }

    public static void createRetroClient(String tag) {
        getInstance().getRetroClient(tag);
    }

    public static Application getApp() {
        return RxRetroHttp.sApplication;
    }

    private void generateOkHttpBuilder() {
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        mOkHttpClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(mTimeOut, TimeUnit.MILLISECONDS)
                .readTimeout(mTimeOut, TimeUnit.MILLISECONDS)
                .writeTimeout(mTimeOut, TimeUnit.MILLISECONDS)
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .addInterceptor(new DateFixInterceptor());
    }

    private void generateRetrofitBuilder() {
        mRetrofitBuilder = new Retrofit.Builder();

    }

    public RxRetroHttp setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        mOkHttpClientBuilder.hostnameVerifier(hostnameVerifier);
        return this;
    }

    public RxRetroHttp setCertificates(InputStream... certificates) {
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(null, null, certificates);
        mOkHttpClientBuilder.sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager);
        return this;
    }

    public RxRetroHttp setCertificates(InputStream bksFile, String password, InputStream...
            certificates) {
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(bksFile, password,
                certificates);
        mOkHttpClientBuilder.sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager);
        return this;
    }

    public RxRetroHttp sslSocketFactory(
            SSLSocketFactory sslSocketFactory, X509TrustManager trustManager) {
        if (sslSocketFactory == null || trustManager == null) {
            return this;
        }
        mOkHttpClientBuilder.sslSocketFactory(sslSocketFactory, trustManager);
        return this;
    }

    public RxRetroHttp setTimeOut(int timeOut) {
        this.mTimeOut = timeOut;
        mOkHttpClientBuilder.connectTimeout(mTimeOut, TimeUnit.MILLISECONDS)
                .readTimeout(mTimeOut, TimeUnit.MILLISECONDS)
                .writeTimeout(mTimeOut, TimeUnit.MILLISECONDS);
        return this;
    }

    public RxRetroHttp addInterceptor(Interceptor interceptor) {
        mOkHttpClientBuilder.addInterceptor(interceptor);
        return this;
    }

    public RxRetroHttp addNetworkInterceptor(Interceptor interceptor) {
        mOkHttpClientBuilder.addNetworkInterceptor(interceptor);
        return this;
    }

    public RxRetroHttp resetInterceptor() {
        mOkHttpClientBuilder.interceptors().clear();
        mOkHttpClientBuilder.networkInterceptors().clear();
        return this;
    }

    public RxRetroHttp setCache(Cache cache) {
        if (cache == null) {
            return this;
        }
        mOkHttpClientBuilder.cache(cache);
        return this;
    }

    public RxRetroHttp setRetryOnConnectFailure(boolean retryOnConnectionFailure) {
        mOkHttpClientBuilder.retryOnConnectionFailure(retryOnConnectionFailure);
        return this;
    }

    public void init(Application app) {
        if (sApplication != null) {
            return;
        }
        RxRetroHttp.sApplication = app;
        if (mIsDebug) {
            Stetho.initializeWithDefaults(app);
        }
        mCommonRetroClient = new SimpleRetroClient().build();
    }

    public void addClient(BaseRetroClient client, String tag) {
        client.build();
        mRetroClientMap.put(tag, client);
    }

    public void generateRetroClient(String tag) {
        SimpleRetroClient retroClient = new SimpleRetroClient().build();
        mRetroClientMap.put(tag, retroClient);
    }

    private BaseRetroClient getRetroClient(String tag) {
        return mRetroClientMap.get(tag);
    }

    private static class UnSafeHostnameVerifier implements HostnameVerifier {
        private String host;

        UnSafeHostnameVerifier(String host) {
            this.host = host;
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return !(this.host == null || "".equals(this.host) || !this.host.contains(hostname));
        }
    }

    public static <T> Observable<T> composeRequest(Observable<T> observable, IBaseApiAction
            apiAction) {
        return observable.compose(composeLifecycle(apiAction))
                .compose(composeApi());
    }

    public static <T> Observable<T> composeRequest(Observable<T> observable, IBaseApiAction
            apiAction, String tag) {
        return observable.compose(composeLifecycle(apiAction))
                .compose(composeApi(tag));
    }

    private static <T> ObservableTransformer<T, T> composeLifecycle(IBaseApiAction apiAction) {
        return upstream -> {
            LifecycleTransformer<T> lifecycleTransformer = apiAction == null ? null : apiAction
                    .getLifecycleTransformer();
            if (lifecycleTransformer != null) {
                upstream = upstream.compose(lifecycleTransformer);
            }
            return upstream;
        };
    }
}
