package cn.zhouyafeng.itchat4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;

import cn.zhouyafeng.itchat4j.utils.Config;
import cn.zhouyafeng.itchat4j.utils.MyHttpClient;
import cn.zhouyafeng.itchat4j.utils.Tools;

public class Wechat {
	private boolean alive = false;
	private String uuid = null;
	private String baseUrl = Config.BASE_URL;
	private MyHttpClient myHttpClient = new MyHttpClient();
	private HttpClient httpClient = HttpClientBuilder.create().build();
	private static Logger logger = Logger.getLogger("Wechat");
	private boolean isLoginIn = false;
	private Map<String, Object> loginInfo = new HashMap<String, Object>();

	Wechat() {
		getQRuuid();
	}

	public int login() throws InterruptedException {
		if (alive) { // 已登陆
			logger.warning("itchat has already logged in.");
			return 0;
		}
		while (true) {
			for (int count = 0; count < 10; count++) {
				logger.info("Getting uuid of QR code.");
				while (getQRuuid() == null) {
					Thread.sleep(1000);
				}
				logger.info("Downloading QR code.");
				Boolean qrStarge = getQR();
				if (qrStarge) { // 获取登陆二维码图片成功
					logger.info("Get QR success");
					break;
				} else if (count == 10) {
					logger.info("Failed to get QR code, please restart the program.");
					System.exit(0);
				}
			}
			logger.info("Please scan the QR code to log in.");
			while (!isLoginIn) {
				String status = checkLogin();
				if (status.equals("200")) {
					isLoginIn = true;
					System.out.println("登陆成功");
				} else if (status.equals("201")) {
					logger.info("Please press confirm on your phone.");
					isLoginIn = false;
				} else {
					break;
				}
			}
			if (isLoginIn)
				break;
			logger.info("Log in time out, reloading QR code");
		}
		this.webInit();
		return 0;
	}

	public String getQRuuid() {
		this.uuid = null;
		String uuidUrl = baseUrl + "/jslogin";
		Map<String, String> params = new HashMap<String, String>();
		params.put("appid", "wx782c26e4c19acffb");
		params.put("fun", "new");
		InputStream in = myHttpClient.doGet(uuidUrl, params);
		if (in != null) {
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String result = "";
			String current;
			try {
				while ((current = br.readLine()) != null) {
					result += current;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			String regEx = "window.QRLogin.code = (\\d+); window.QRLogin.uuid = (\\S+?);";
			Matcher matcher = Tools.getMatcher(regEx, result);
			if (matcher.find()) {
				if ((matcher.group(1).equals("200"))) {
					String orgUuid = matcher.group(2); // "Aakzcf8mLQ=="
					uuid = orgUuid.substring(1, orgUuid.length() - 1); // 去掉双引号
				}
			}
		}
		return uuid;
	}

	/**
	 * 生成登陆二维码图片
	 * 
	 * @author Email:zhouyaphone@163.com
	 * @date 2017年4月8日 下午9:55:22
	 * @return
	 */
	public boolean getQR() {
		String qrUrl = baseUrl + "/qrcode/" + this.uuid;
		InputStream in = myHttpClient.doGet(qrUrl, null);
		String qrPath = Config.getLocalPath() + File.separator + "QR.jpg";
		OutputStream out = null;
		try {
			int byteCount = 0;
			out = new FileOutputStream(qrPath);
			byte[] bytes = new byte[1024];
			while ((byteCount = in.read(bytes)) != -1) {
				out.write(bytes, 0, byteCount);
			}
			in.close();
			out.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		// 打开二维码图片
		Tools.printQr(qrPath);
		return true;
	}

	/**
	 * 检查登陆状态
	 * 
	 * @author Email:zhouyaphone@163.com
	 * @date 2017年4月8日 下午11:22:16
	 * @return
	 */
	public String checkLogin() {
		String checkUrl = baseUrl + "/cgi-bin/mmwebwx-bin/login";
		Long localTime = new Date().getTime();
		Map<String, String> params = new HashMap<String, String>();
		params.put("loginicon", "true");
		params.put("uuid", uuid);
		params.put("tip", "0");
		params.put("r", String.valueOf(localTime / 1579L));
		params.put("_", String.valueOf(localTime));
		BufferedReader br = new BufferedReader(new InputStreamReader(myHttpClient.doGet(checkUrl, params)));
		String result = "";
		String current;
		try {
			while ((current = br.readLine()) != null) {
				result += current;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		String regEx = "window.code=(\\d+)";
		Matcher matcher = Tools.getMatcher(regEx, result);
		if (matcher.find()) {
			if (matcher.group(1).equals("200")) { // 已登陆
				processLoginInfo(result);
				return "200";
			} else if (matcher.group(1).equals("201")) { // 已扫描，未登陆
				return "201";
			}
		}
		return "400";
	}

	/**
	 * 处理登陆信息
	 * 
	 * @author Email:zhouyaphone@163.com
	 * @date 2017年4月9日 下午12:16:26
	 * @param result
	 */
	public void processLoginInfo(String result) {
		String regEx = "window.redirect_uri=\"(\\S+)\";";
		Matcher matcher = Tools.getMatcher(regEx, result);
		if (matcher.find()) {
			String originalUrl = matcher.group(1);
			String url = originalUrl.substring(0, originalUrl.lastIndexOf('/')); // https://wx2.qq.com/cgi-bin/mmwebwx-bin
			loginInfo.put("url", url);
			Map<String, List<String>> possibleUrlMap = getPossibleUrlMap();
			Iterator<Entry<String, List<String>>> iterator = possibleUrlMap.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, List<String>> entry = iterator.next();
				String indexUrl = entry.getKey();
				String fileUrl = "https://" + entry.getValue().get(0) + "/cgi-bin/mmwebwx-bin";
				String syncUrl = "https://" + entry.getValue().get(1) + "/cgi-bin/mmwebwx-bin";
				// System.out.println(fileUrl);
				// System.out.println(syncUrl);
				if (loginInfo.get("url").toString().contains(indexUrl)) {
					loginInfo.put("fileUrl", fileUrl);
					loginInfo.put("syncUrl", syncUrl);
					break;
				}
			}
			if (loginInfo.get("fileUrl") == null && loginInfo.get("syncUrl") == null) {
				loginInfo.put("fileUrl", url);
				loginInfo.put("syncUrl", url);
			}
			loginInfo.put("deviceid", "e" + String.valueOf(new Random().nextLong()).substring(1, 16)); // 生成15位随机数
			loginInfo.put("BaseRequest", new ArrayList<String>());
			BufferedReader br = new BufferedReader(new InputStreamReader(myHttpClient.doGet(originalUrl, null)));
			String text = "";
			String current;
			try {
				while ((current = br.readLine()) != null) {
					text += current;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			Document doc = Tools.xmlParser(text);
			Map<String, String> baseRequest = new HashMap<String, String>();
			if (doc != null) {
				loginInfo.put("skey", doc.getElementsByTagName("skey").item(0).getFirstChild().getNodeValue());
				baseRequest.put("Skey", (String) loginInfo.get("skey"));
				loginInfo.put("wxsid", doc.getElementsByTagName("wxsid").item(0).getFirstChild().getNodeValue());
				baseRequest.put("Sid", (String) loginInfo.get("wxsid"));
				loginInfo.put("wxuin", doc.getElementsByTagName("wxuin").item(0).getFirstChild().getNodeValue());
				baseRequest.put("Uin", (String) loginInfo.get("wxuin"));
				loginInfo.put("pass_ticket",
						doc.getElementsByTagName("pass_ticket").item(0).getFirstChild().getNodeValue());
				baseRequest.put("DeviceID", (String) loginInfo.get("pass_ticket"));
				loginInfo.put("baseRequest", baseRequest);
			}

		}
	}

	Map<String, List<String>> getPossibleUrlMap() {
		Map<String, List<String>> possibleUrlMap = new HashMap<String, List<String>>();
		possibleUrlMap.put("wx2.qq.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx2.qq.com");
				add("webpush.wx2.qq.com");
			}
		});
		possibleUrlMap.put("wx8.qq.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx8.qq.com");
				add("webpush.wx8.qq.com");
			}
		});
		possibleUrlMap.put("qq.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx.qq.com");
				add("webpush.wx.qq.com");
			}
		});
		possibleUrlMap.put("web2.wechat.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.web2.wechat.com");
				add("webpush.web2.wechat.com");
			}
		});
		possibleUrlMap.put("wechat.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.web.wechat.com");
				add("webpush.web.wechat.com");
			}
		});
		return possibleUrlMap;
	}

	boolean webInit() {
		String url = loginInfo.get("url") + "/webwxinit?&r=" + String.valueOf(new Date().getTime());
		System.out.println(url);
		Map<String, String> baseRequest = (Map<String, String>) loginInfo.get("baseRequest");
		// Map<String, Map<String, String>> paramMap =
		// loginInfo.get("BaseRequest");
		// paramMap.put("BaseRequest", baseRequest);
		// String paramsStr = JSON.toJSONString(paramMap);
		// System.out.println(JSON.toJSONString(paramMap));
		String paramsStr = String.format(
				"{\"BaseRequest\":{\"Uin\":\"%s\", \"Skey\":\"%s\",\"DeviceID\":\"%s\", \"Sid\":\"%s\"}}",
				baseRequest.get("Uin"), baseRequest.get("Skey"), baseRequest.get("DeviceID"), baseRequest.get("Sid"));
		System.out.println(paramsStr);
		// System.out.println(paramsStr);
		// {"BaseRequest": {"Uin": "264833395", "Skey":
		// "@crypt_6b6c25c8_dddc7f7439208530c2372055ae30983f", "DeviceID":
		// "e%2Fqe6nMDCwZvxF%2F8vfwx0W8R5sB%2FXFyGJBKZlvgGnE0%3D", "Sid":
		// "akzVqrw0haClkH0C"}}
		HttpPost request = new HttpPost(url);
		try {
			StringEntity params = new StringEntity(paramsStr);
			request.setHeader("Content-type", "application/json; charset=utf-8");
			request.setHeader("User-Agent", Config.USER_AGENT);
			HttpResponse response = httpClient.execute(request);
			System.out.println(EntityUtils.toString(response.getEntity()));

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

}