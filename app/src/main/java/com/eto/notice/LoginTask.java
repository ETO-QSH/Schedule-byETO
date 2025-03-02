package com.eto.notice;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginTask extends AsyncTask<Void, Void, String> {
    private static final String LOGIN_URL = "https://zhlgd.whut.edu.cn/tpass/login?service=https%3A%2F%2Fjwxt.whut.edu.cn%2Fjwapp%2Fsys%2Fhomeapp%2Findex.do%3FforceCas%3D1";
    private static final String RSA_URL = "https://zhlgd.whut.edu.cn/tpass/rsa?skipWechat=true";
    private static final String XNX_URL = "https://jwxt.whut.edu.cn/jwapp/sys/homeapp/api/home/currentUser.do";
    private static final String KB_URL = "https://jwxt.whut.edu.cn/jwapp/sys/homeapp/api/home/student/courses.do";
    private final OkHttpClient client;
    private final String username;
    private final String password;
    private final int minutes;

    public interface LoginCallback {
        void onLoginSuccess(JSONObject json);
        void onLoginFailure(String err);
    }

    private final LoginCallback callback;
    // 修改构造函数
    public LoginTask(String username, String password, int minutes, LoginCallback callback) {
        this.username = username;
        this.password = password;
        this.minutes = minutes;
        this.callback = callback;

        // 配置信任所有证书的OkHttpClient（仅用于测试，生产环境不推荐）
        client = new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    private final List<Cookie> allCookies = new ArrayList<>(); // 存储所有 Cookie

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        allCookies.addAll(cookies); // 存储所有 Cookie，不区分域名
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        // 返回所有 Cookie，无论域名是否匹配
                        return new ArrayList<>(allCookies);
                    }
                })
                .build();
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            if (isCancelled()) return null;
            // Step 1: 获取登录页面并提取参数
            Request loginPageRequest = new Request.Builder()
                    .url(LOGIN_URL)
                    .build();
            Response loginPageResponse = client.newCall(loginPageRequest).execute();
            String html = loginPageResponse.body().string();
            Document doc = Jsoup.parse(html);
            Element ltElement = doc.selectFirst("input#lt");
            Element executionElement = doc.selectFirst("input[name=execution]");
            Element eventIdElement = doc.selectFirst("input[name=_eventId]");

            String lt = ltElement.attr("value");
            String execution = executionElement.attr("value");
            String eventId = eventIdElement.attr("value");

            if (isCancelled()) return null;
            // Step 2: 获取RSA公钥
            Request rsaRequest = new Request.Builder()
                    .url(RSA_URL)
                    .post(RequestBody.create("", null))
                    .build();
            Response rsaResponse = client.newCall(rsaRequest).execute();
            String rsaJson = rsaResponse.body().string();
            JSONObject rsaJsonObj = new JSONObject(rsaJson);
            String publicKeyStr = rsaJsonObj.getString("publicKey");

            if (isCancelled()) return null;
            // Step 3: 处理公钥并加密
            byte[] publicKeyBytes = Base64.decode(publicKeyStr, Base64.DEFAULT);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec);

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);

            byte[] encryptedUsername = cipher.doFinal(username.getBytes(StandardCharsets.UTF_8));
            byte[] encryptedPassword = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));

            String ul = Base64.encodeToString(encryptedUsername, Base64.NO_WRAP);
            String pl = Base64.encodeToString(encryptedPassword, Base64.NO_WRAP);

            if (isCancelled()) return null;
            // Step 4: 提交登录表单
            FormBody formBody = new FormBody.Builder()
                    .add("rsa", "")
                    .add("ul", ul)
                    .add("pl", pl)
                    .add("lt", lt)
                    .add("execution", execution)
                    .add("_eventId", eventId)
                    .build();

            Headers headers = new Headers.Builder()
                    .add("Referer", LOGIN_URL)
                    .add("cache-control", "max-age=0")
                    .add("sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Microsoft Edge\";v=\"133\", \"Chromium\";v=\"133\"")
                    .add("sec-ch-ua-mobile", "?0")
                    .add("sec-ch-ua-platform", "\"Windows\"")
                    .add("origin", "https://zhlgd.whut.edu.cn")
                    .add("content-type", "application/x-www-form-urlencoded")
                    .add("upgrade-insecure-requests", "1")
                    .add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .add("sec-fetch-site", "same-origin")
                    .add("sec-fetch-mode", "navigate")
                    .add("sec-fetch-user", "?1")
                    .add("sec-fetch-dest", "document")
                    .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
                    .add("priority", "u=0, i")
                    .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 Edg/133.0.0.0")
                    .build();

            Request loginRequest = new Request.Builder()
                    .url(LOGIN_URL)
                    .post(formBody)
                    .headers(headers)
                    .build();

            Response loginResponse = client.newCall(loginRequest).execute();
            String loginResponseHtml = loginResponse.body().string();

            // 检查 loginResponseHtml 是否为空
            if (loginResponseHtml == null || loginResponseHtml.trim().isEmpty()) {
                return "null loginResponseHtml";
            }

            // 使用 Jsoup 解析 HTML
            Document docx = Jsoup.parse(loginResponseHtml);

            // 查找 script 标签
            Elements scripts = docx.select("script");

            String hrefValue = null;
            for (Element script : scripts) {
                String scriptContent = script.data(); // 获取 script 标签的内容
                if (scriptContent.contains("location.href")) {
                    // 使用正则表达式提取 location.href 的值
                    hrefValue = scriptContent.replaceAll(".*location\\.href\\s*=\\s*'([^']*)'.*", "$1");
                }
            }

            // Step 5: 获取最新课表
            if (isCancelled()) return null;
            Headers commonHeaders = new Headers.Builder()
                    .add("referer", "https://jwxt.whut.edu.cn" + hrefValue.trim())
                    .add("sec-ch-ua", "\"Not(A:Brand\";v=\"99\", \"Microsoft Edge\";v=\"133\", \"Chromium\";v=\"133\"")
                    .add("sec-ch-ua-mobile", "?0")
                    .add("sec-ch-ua-platform", "\"Windows\"")
                    .add("upgrade-insecure-requests", "1")
                    .add("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36 Edg/133.0.0.0")
                    .add("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .add("sec-fetch-site", "none")
                    .add("sec-fetch-mode", "navigate")
                    .add("sec-fetch-user", "?1")
                    .add("sec-fetch-dest", "document")
                    .add("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
                    .add("priority", "u=0, i")
                    .build();

            HttpUrl xnxqHttpUrl = HttpUrl.parse(XNX_URL).newBuilder()
                    .build();

            Request xnxqRequest = new Request.Builder()
                    .url(xnxqHttpUrl)
                    .headers(commonHeaders)
                    .build();

            Response xnxqResponse = client.newCall(xnxqRequest).execute();
            String responseJson = xnxqResponse.body().string();

            // 获取 xnxqdm 的值
            JSONObject jsonResponse = new JSONObject(responseJson);
            JSONObject datas = jsonResponse.getJSONObject("datas");
            String xnxqdm = datas.getJSONObject("welcomeInfo").getString("xnxqdm");

            if (isCancelled()) return null;
            // Step 6: 获取课程数据
            HttpUrl kbHttpUrl = HttpUrl.parse(KB_URL).newBuilder()
                    .addQueryParameter("termCode", xnxqdm)
                    .build();

            Request kbRequest = new Request.Builder()
                    .url(kbHttpUrl)
                    .headers(commonHeaders)
                    .build();

            Response kbResponse = client.newCall(kbRequest).execute();
            String responseString = kbResponse.body().string();
            Log.v("TAG", "responseString " + responseString);
            if (isCancelled()) return null;
            return responseString;

        } catch (Exception e) {
            if (isCancelled()) {
                return null;
            }
            return null;
        }
    }

    @Override
    protected void onPostExecute(String result) {
        try {
            new JSONObject(result);
            JSONObject processedJson = GetConverter.handleResult(result, minutes);
            if (callback != null) {
                callback.onLoginSuccess(processedJson);
            }
        } catch (JSONException e) {
            if (callback != null) {
                callback.onLoginFailure(result);
            }
        }
    }
}