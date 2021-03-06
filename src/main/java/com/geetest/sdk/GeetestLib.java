/**
 * @(#)Result.JAVA, 2020年04月02日.
 *
 * Copyright 2020 GEETEST, Inc. All rights reserved.
 * GEETEST PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.geetest.sdk;

import com.geetest.demo.GeetestConfig;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;

import com.geetest.demo.GeetestConfig;

/**
 * sdk lib包，核心逻辑。
 *
 * @author liuquan@geetest.com
 */
public class GeetestLib {

    /**
     * 公钥
     */
    private String geetestId;

    /**
     * 私钥
     */
    private String geetestKey;

    /**
     * 以下字段值固定，无需改动
     */
    private static final String API_URL = "http://api.geetest.com";

    private static final String REGISTER_URL = "/register.php";

    private static final String VALIDATE_URL = "/validate.php";

    private static final String JSON_FORMAT = "1";

    private static final boolean NEW_FAILBACK = true;

    public GeetestLib(String geetestId, String geetestKey) {
        this.geetestId = geetestId;
        this.geetestKey = geetestKey;
    }

    /**
     * 一次验证
     */
    public GeetestLibResult register(HashMap<String, String> paramMap) {
        this.gtlog("register()：++++++++ 开始一次验证register ++++++++");
        GeetestLibResult libResult = new GeetestLibResult();
        String origin_challenge = requestRegister(paramMap, libResult);
        // origin_challenge为空代表失败
        if (origin_challenge == null || origin_challenge.isEmpty()) {
            libResult.setStatus(0);
            libResult.setMsg("请求极验register接口失败，后续流程走failback模式");
            libResult.setData(this.buildFailRegisterResult(libResult));
        } else {
            libResult.setStatus(1);
            libResult.setData(
                this.buildSuccessRegisterResult(origin_challenge, libResult));
        }
        this.gtlog("register()：一次验证register：lib包返回信息=" + libResult);
        return libResult;
    }

    /**
     * 向极验发送一次验证的请求，GET方式
     */
    private String requestRegister(HashMap<String, String> paramMap,
        GeetestLibResult libResult) {
        try {
            Iterator<String> it = paramMap.keySet().iterator();
            StringBuilder paramStr = new StringBuilder();
            while (it.hasNext()) {
                String key = it.next();
                if (key == null || key.isEmpty() || paramMap.get(key) == null
                    || paramMap.get(key).isEmpty()) {
                    continue;
                }
                paramStr.append("&").append(key).append("=")
                    .append(URLEncoder.encode(paramMap.get(key), "utf-8"));
            }
            String url = this.API_URL + this.REGISTER_URL + "?";
            String param = String.format("gt=%s&json_format=%s", this.geetestId,
                this.JSON_FORMAT) + paramStr.toString();
            this.gtlog("requestRegister()：一次验证register：请求url=" + url + param);
            String responseStr = readContentFromGet(url + param);
            if ("fail".equals(responseStr)) {
                this.gtlog(
                    "requestRegister()：一次验证register：向极验请求失败，将进入failback模式，readContentFromGet()返回值="
                        + responseStr);
                return "";
            } else {
                this.gtlog(
                    "requestRegister()：一次验证register：向极验请求正常，readContentFromGet()返回值="
                        + responseStr);
                JSONObject jsonObject = new JSONObject(responseStr);
                String origin_challenge = jsonObject.getString("challenge");
                if (origin_challenge.length() != 32) {
                    this.gtlog(
                        "requestRegister()：一次验证register：极验返回数据标识失败，请检查id和key配置，id="
                            + this.geetestId + "，key=" + this.geetestKey);
                    libResult.setMsg("极验返回数据标识失败，id=" + this.geetestId + "，key="
                        + this.geetestKey);
                    return "";
                } else {
                    return origin_challenge;
                }
            }
        } catch (Exception e) {
            this.gtlog("requestRegister()：一次验证register：发生异常， " + e.toString());
            libResult.setMsg("向极验发送请求发生异常， " + e.toString());
        }
        return "";
    }

    /**
     * 一次验证失败，构造返回值
     */
    private String buildFailRegisterResult(GeetestLibResult libResult) {
        Long rnd = Math.round(Math.random() * 100);
        String md5Str = this.md5Encode(rnd + "");
        String challenge = md5Str;
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("success", 0);
            jsonObject.put("gt", this.geetestId);
            jsonObject.put("challenge", challenge);
            jsonObject.put("new_captcha", this.NEW_FAILBACK);
        } catch (JSONException e) {
            this.gtlog("buildFailRegisterResult()：一次验证register：构造失败返回值异常, "
                + e.toString());
            libResult.setMsg("构造失败返回值发生异常，" + e.toString());
        }
        this.gtlog("buildFailRegisterResult()：一次验证register：构造失败返回值，返回值="
            + jsonObject.toString());
        return jsonObject.toString();
    }

    /**
     * 一次验证成功，构造返回值
     */
    private String buildSuccessRegisterResult(String origin_challenge,
        GeetestLibResult libResult) {
        JSONObject jsonObject = new JSONObject();
        String challenge = this.md5Encode(origin_challenge + this.geetestKey);
        try {
            jsonObject.put("success", 1);
            jsonObject.put("gt", this.geetestId);
            jsonObject.put("challenge", challenge);
            jsonObject.put("new_captcha", this.NEW_FAILBACK);
        } catch (JSONException e) {
            this.gtlog("buildSuccessRegisterResult()：一次验证register：构造成功返回值异常, "
                + e.toString());
            libResult.setMsg("构造成功返回值发生异常，" + e.toString());
        }
        this.gtlog("buildSuccessRegisterResult()：一次验证register：构造成功返回值，返回值="
            + jsonObject.toString());
        return jsonObject.toString();
    }

    /**
     * 正常流程下（即一次验证请求成功），二次验证
     */
    public GeetestLibResult successValidate(String challenge, String validate,
        String seccode, HashMap<String, String> paramMap) {
        this.gtlog(String.format(
            "successValidate()：++++++++ 开始二次验证validate，正常模式 ++++++++，challenge=%s，validate=%s，seccode=%s",
            challenge, validate, seccode));
        GeetestLibResult libResult = new GeetestLibResult();
        if (!checkSuccessValidateParam(challenge, validate, seccode,
            libResult)) {
            libResult.setStatus(0);
            libResult.setData(this.buildFailValidateResult(libResult));
            this.gtlog("successValidate()：二次验证validate：正常模式，校验请求参数失败");
            return libResult;
        }
        String response_seccode = requestValidate(challenge, validate, seccode,
            paramMap, libResult);
        // response_seccode 为空代表失败
        if (response_seccode == null || response_seccode.isEmpty()) {
            libResult.setStatus(0);
            libResult.setMsg("正常模式，请求极验validate接口失败");
        } else {
            String request_seccode_md5 = this.md5Encode(seccode);
            if (response_seccode.equals(request_seccode_md5)) {
                libResult.setStatus(1);
                libResult.setData(this.buildSuccessValidateResult(libResult));
            } else {
                libResult.setStatus(0);
                libResult.setMsg("正常模式，seccode与请求极验返回值作md5校验失败");
                this.gtlog(String.format(
                    "successValidate()：二次验证validate：正常模式，seccode与请求极验返回值作md5校验失败，request_seccode_md5=%s，response_seccode=%s",
                    request_seccode_md5, response_seccode));
                libResult.setData(this.buildFailValidateResult(libResult));
            }
        }
        this.gtlog("successValidate()：二次验证validate：正常模式，lib包返回信息=" + libResult);
        return libResult;
    }

    /**
     * 异常流程下（即failback模式），二次验证
     * 注意：由于是failback模式，初衷是保证验证业务不会中断正常业务，所以此处只作简单的参数校验，可自行设计逻辑。
     */
    public GeetestLibResult failValidate(String challenge, String validate,
        String seccode) {
        this.gtlog(String.format(
            "failValidate()：++++++++ 开始二次验证validate，异常模式 ++++++++，challenge=%s，validate=%s，seccode=%s",
            challenge, validate, seccode));
        GeetestLibResult libResult = new GeetestLibResult();
        if (challenge == null || challenge.isEmpty() || validate == null
            || validate.isEmpty() || seccode == null || seccode.isEmpty()) {
            libResult.setStatus(0);
            libResult.setData(this.buildFailValidateResult(libResult));
            libResult.setMsg("异常模式，请求参数不可为空");
            this.gtlog("failValidate()：二次验证validate：异常模式，校验请求参数失败");
        } else {
            libResult.setStatus(1);
            libResult.setData(this.buildSuccessValidateResult(libResult));
        }
        this.gtlog("failValidate()：二次验证validate：异常模式，lib包返回信息=" + libResult);
        return libResult;
    }

    /**
     * 向极验发送二次验证的请求，POST方式
     *
     * @return 响应数据，字符串形式
     */
    private String requestValidate(String challenge, String validate,
        String seccode, HashMap<String, String> paramMap,
        GeetestLibResult libResult) {
        try {
            Iterator<String> it = paramMap.keySet().iterator();
            StringBuilder paramStr = new StringBuilder();
            while (it.hasNext()) {
                String key = it.next();
                if (key == null || key.isEmpty() || paramMap.get(key) == null
                    || paramMap.get(key).isEmpty()) {
                    continue;
                }
                paramStr.append("&").append(key).append("=")
                    .append(URLEncoder.encode(paramMap.get(key), "utf-8"));
            }
            String param = String.format(
                "challenge=%s&validate=%s&seccode=%s&json_format=%s", challenge,
                validate, seccode, this.JSON_FORMAT) + paramStr.toString();
            String url = this.API_URL + this.VALIDATE_URL;
            this.gtlog("requestValidate()：二次验证validate：正常模式，请求urL=" + url
                + ", param=" + param);
            String responseStr = readContentFromPost(url, param);
            if ("fail".equals(responseStr)) {
                this.gtlog(
                    "requestValidate()：二次验证validate：正常模式，向极验请求失败，readContentFromPost()返回值="
                        + responseStr);
                return "";
            } else {
                this.gtlog(
                    "requestValidate()：二次验证validate：正常模式，向极验请求正常，readContentFromPost()返回值="
                        + responseStr);
                JSONObject jsonObject = new JSONObject(responseStr);
                return jsonObject.getString("seccode");
            }
        } catch (Exception e) {
            this.gtlog(
                "requestValidate()：二次验证validate：正常模式，发生异常, " + e.toString());
            libResult.setMsg("正常模式，发生异常，" + e.toString());
        }
        return "";
    }

    /**
     * 检查二次验证正常模式下的请求参数是否合法
     */
    private boolean checkSuccessValidateParam(String challenge, String validate,
        String seccode, GeetestLibResult libResult) {
        if (challenge == null || challenge.isEmpty() || validate == null
            || validate.isEmpty() || seccode == null || seccode.isEmpty()) {
            libResult.setMsg("正常模式，本地校验，参数challenge、validate、seccode不可为空");
            return false;
        }
        String geetestkey_geetest_challenge_md5 = this
            .md5Encode(this.geetestKey + "geetest" + challenge);
        if (!validate.equals(geetestkey_geetest_challenge_md5)) {
            this.gtlog(String.format(
                "checkSuccessValidateParam()：二次验证validate：正常模式，validate与相关参数作md5校验失败，validate=%s，geetestkey_geetest_challenge_md5=%s",
                validate, geetestkey_geetest_challenge_md5));
            libResult.setMsg("正常模式，本地校验，validate与相关参数作md5校验失败");
            return false;
        }
        return true;
    }

    /**
     * 二次验证失败，构造返回值
     */
    private String buildFailValidateResult(GeetestLibResult libResult) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("status", "fail");
        } catch (JSONException e) {
            this.gtlog("buildFailValidateResult()：二次验证validate：构造失败返回值异常, "
                + e.toString());
            libResult.setMsg("构造失败返回值异常, " + e.toString());
        }
        this.gtlog("buildFailValidateResult()：二次验证validate：构造失败返回值，返回值="
            + jsonObject.toString());
        return jsonObject.toString();
    }

    /**
     * 二次验证成功，构造返回值
     */
    private String buildSuccessValidateResult(GeetestLibResult libResult) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("status", "success");
        } catch (JSONException e) {
            this.gtlog("buildSuccessValidateResult()：二次验证validate：构造成功返回值异常, "
                + e.toString());
            libResult.setMsg("构造成功返回值异常, " + e.toString());
        }
        this.gtlog("buildSuccessValidateResult()：二次验证validate：构造成功返回值，返回值="
            + jsonObject.toString());
        return jsonObject.toString();

    }

    /**
     * 发送GET请求，获取服务器返回结果
     * 
     * @return 服务器返回结果
     * @throws IOException
     */
    private String readContentFromGet(String URL) throws IOException {
        try {
            URL getUrl = new URL(URL);
            HttpURLConnection connection = (HttpURLConnection) getUrl
                .openConnection();
            connection.setConnectTimeout(2000);// 设置连接主机超时（单位：毫秒）
            connection.setReadTimeout(2000);// 设置从主机读取数据超时（单位：毫秒）
            // 建立与服务器的连接，并未发送数据
            connection.connect();
            if (connection.getResponseCode() == 200) {
                // 发送数据到服务器并使用Reader读取返回的数据
                StringBuffer sBuffer = new StringBuffer();
                InputStream inStream = null;
                byte[] buf = new byte[1024];
                inStream = connection.getInputStream();
                for (int n; (n = inStream.read(buf)) != -1;) {
                    sBuffer.append(new String(buf, 0, n, "UTF-8"));
                }
                inStream.close();
                connection.disconnect();// 断开连接
                return sBuffer.toString();
            }
        } catch (Exception e) {
            this.gtlog("readContentFromGet()：发送GET请求，发生异常, " + e.toString());
        }
        return "fail";
    }

    /**
     * 发送POST请求，获取服务器返回结果
     * 
     * @return 服务器返回结果
     * @throws IOException
     */
    private String readContentFromPost(String URL, String data)
        throws IOException {
        try {
            URL postUrl = new URL(URL);
            HttpURLConnection connection = (HttpURLConnection) postUrl
                .openConnection();
            connection.setConnectTimeout(2000);// 设置连接主机超时（单位：毫秒）
            connection.setReadTimeout(2000);// 设置从主机读取数据超时（单位：毫秒）
            connection.setRequestMethod("POST");
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");
            // 建立与服务器的连接，并未发送数据
            connection.connect();
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                connection.getOutputStream(), "utf-8");
            outputStreamWriter.write(data);
            outputStreamWriter.flush();
            outputStreamWriter.close();
            if (connection.getResponseCode() == 200) {
                // 发送数据到服务器并使用Reader读取返回的数据
                StringBuffer sBuffer = new StringBuffer();
                InputStream inStream = null;
                byte[] buf = new byte[1024];
                inStream = connection.getInputStream();
                for (int n; (n = inStream.read(buf)) != -1;) {
                    sBuffer.append(new String(buf, 0, n, "UTF-8"));
                }
                inStream.close();
                connection.disconnect();// 断开连接
                return sBuffer.toString();
            }
        } catch (Exception e) {
            this.gtlog("readContentFromPost()：发送POST请求，发生异常, " + e.toString());
        }
        return "fail";
    }

    /**
     * md5 加密
     */
    private String md5Encode(String plainText) {
        String re_md5 = new String();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte b[] = md.digest();
            int i;
            StringBuffer buf = new StringBuffer("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }

            re_md5 = buf.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return re_md5;
    }

    /**
     * 输出debug信息，需要开启开关
     */
    public void gtlog(String message) {
        if (GeetestConfig.isDebug) {
            System.out.println("this.gtlog：" + message);
        }
    }

}
