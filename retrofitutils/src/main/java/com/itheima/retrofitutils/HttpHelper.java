package com.itheima.retrofitutils;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.itheima.retrofitutils.listener.HttpResponseListener;
import com.itheima.retrofitutils.listener.UploadListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

/**
 * Created by lyl on 2016/10/3.
 */

public final class HttpHelper {
    private volatile static WeakReference<HttpHelper> sInstance;
    private final Retrofit mRetrofit;

    private static String sBaseUrl;

    public static void setBaseUrl(String baseUrl) {
        sBaseUrl = baseUrl;
    }

    public static String getBaseUrl() {
        return sBaseUrl;
    }

    private HttpHelper() {
        Retrofit.Builder builder = new Retrofit.Builder();
        if (TextUtils.isEmpty(getBaseUrl())) {
            throw new NullPointerException("init(Context,httpBaseUrl)：httpBaseUrl is not null");
        }
        mRetrofit = builder.baseUrl(getBaseUrl()).build();
    }

    public static HttpHelper getInstance() {
        if (sInstance == null || sInstance.get() == null) {
            synchronized (HttpHelper.class) {
                if (sInstance == null || sInstance.get() == null) {
                    sInstance = new WeakReference<HttpHelper>(new HttpHelper());
                }
            }
        }
        return sInstance.get();
    }


    public static <T> Call getAsync(String apiUrl, @HeaderMap Map<String, Object> headers, Map<String, Object> paramMap, final HttpResponseListener<T> httpResponseListener) {
        if (paramMap == null) {
            paramMap = new HashMap<>();
        }
        if (headers == null) {
            headers = new HashMap<>();
        }
        HttpService httpService = getInstance().mRetrofit.create(HttpService.class);
        Call<ResponseBody> call = httpService.get(apiUrl, headers, paramMap);
        parseNetData(call, httpResponseListener);
        return call;
    }

    public static <T> Call postAsync(String apiUrl, @HeaderMap Map<String, Object> headers, Map<String, Object> paramMap, HttpResponseListener<T> httpResponseListener) {
        if (paramMap == null) {
            paramMap = new HashMap<>();
        }
        if (headers == null) {
            headers = new HashMap<>();
        }
        HttpService httpService = getInstance().mRetrofit.create(HttpService.class);
        Call<ResponseBody> call = httpService.post(apiUrl, headers, paramMap);

        parseNetData(call, httpResponseListener);
        return call;
    }

    public static Call upload(Request request, final UploadListener uploadListener) {
        if (request.getUploadFiles() == null || !request.getUploadFiles().get(0).exists()) {
            new FileNotFoundException("file does not exist(文件不存在)").printStackTrace();
        }
        Map<String, RequestBody> requestBodyMap = new HashMap<>();
        RequestBody requestBody = RequestBody.create(request.getMediaType(), request.getUploadFiles().get(0));
        requestBodyMap.put("file[]\"; filename=\"" + request.getUploadFiles().get(0).getName(), requestBody);

        String httpUrl = request.getApiUlr().trim();
        String tempUrl = httpUrl.substring(0, httpUrl.length() - 1);
        String baseUrl = tempUrl.substring(0, tempUrl.lastIndexOf(File.separator) + 1);
        if (L.isDebug) {
            L.i("httpUrl:" + httpUrl);
            L.i("tempUrl:" + tempUrl);
            L.i("baseUrl:" + baseUrl);
            L.i("apiUrl:" + httpUrl.substring(baseUrl.length()));
        }
        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(new ChunkingConverterFactory(requestBody, uploadListener))
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(baseUrl)
                .build();
        HttpService service = retrofit.create(HttpService.class);

        Call<ResponseBody> model = service.upload(
                httpUrl.substring(baseUrl.length())
                , "uploadDes"
                , requestBodyMap);

        model.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                uploadListener.onResponse(call, response);
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                uploadListener.onFailure(call, t);
            }
        });
        return model;
    }


    private static <T> void parseNetData(Call<ResponseBody> call, final HttpResponseListener<T> httpResponseListener) {
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                try {
                    String json = response.body().string();
                    if (L.isDebug) {
                        L.i("response data:" + json);
                    }
                    if (!String.class.equals(httpResponseListener.getType())) {
                        Gson gson = new Gson();
                        T t = gson.fromJson(json, httpResponseListener.getType());
                        httpResponseListener.onResponse(t);
                    } else {
                        httpResponseListener.onResponse((T) json);
                    }
                } catch (Exception e) {
                    if (L.isDebug) {
                        L.e("Http Exception:", e);
                    }
                    httpResponseListener.onFailure(call, e);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                httpResponseListener.onFailure(call, t);
            }
        });
    }


    public static interface HttpService<T> {
        @GET
        public Call<ResponseBody> get(@Url String url, @HeaderMap Map<String, String> headers, @QueryMap Map<String, Object> param);

        @FormUrlEncoded
        @POST
        public Call<ResponseBody> post(@Url String url, @HeaderMap Map<String, String> headers, @FieldMap Map<String, Object> param);

        @Multipart
        @POST
        Call<String> upload(@Url String url, @Part("filedes") String des, @PartMap Map<String, RequestBody> params);
    }
}
