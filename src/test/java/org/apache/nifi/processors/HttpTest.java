package org.apache.nifi.processors;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import bean.JsonKdm;
import bean.KdmData;
import bean.KdmData2;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class HttpTest {
	public static void main(String[] args) throws IOException {
		// String path =
		// "C:\\Users\\Administrator\\Desktop\\testFlowFile\\file4";
		// String path2 =
		// "C:\\Users\\Administrator\\Desktop\\testFlowFile\\file4f.txt";
		// File file = new File(path);
		// File file2 = new File(path2);
		// File[] files = file.listFiles();
		// Arrays.sort(files);
		// for (int i = 0; i < files.length; i++) {
		// System.out.println(files[i]);
		// if (files[i].isFile()) {
		// InputStreamReader reader = new InputStreamReader(new
		// FileInputStream(files[i]));
		// BufferedWriter bw = new BufferedWriter(new FileWriter(file2,true));
		// BufferedReader br = new BufferedReader(reader);
		// String line = "";
		// line = br.readLine();
		// bw.write(line);
		// bw.newLine();
		// try {
		//
		// br.close();
		// bw.close();
		// reader.close();
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// }
		// }

//		CloseableHttpClient httpclient = HttpClients.createDefault();
//		String jsonData1 =
//				 "[{\"code\":\"HNQW.BS4_35KV分段Ic\",\"data\":[{\"time\":1528889430084,\"value\":92.6085}]}]";
//		try {
//			HttpPost httpPost = new HttpPost("http://192.168.11.2:8089/v2/rtdata?alias=KKS");
//			StringEntity se = new StringEntity(jsonData1,Charset.forName("utf-8"));
//			se.setContentType("application/json;charset=utf-8");
////			se.setContentEncoding("utf-8");
//			httpPost.setEntity(se);
//			CloseableHttpResponse response2 = httpclient.execute(httpPost);
//			System.out.println(jsonData1);
//			try {
//				HttpEntity entity2 = response2.getEntity();
//				String res = EntityUtils.toString(entity2);
//				if (res.equals("{}")) {
//					System.out.println("写入成功！");
//				}
//			} finally {
//				response2.close();
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		} finally {
//			try {
//				httpclient.close();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
		String url="http://192.168.11.2:8081/data/v2/rtdata";
		String data="{\"1_HNAB.AB_TOT_AINTLTMP\": {\"data\": {\"time\": 1530675524008,\"value\": 555}}}";
		String jsondata=getJsonToData(data);
		String result=putJsonDataHttp(jsondata,url);
		System.out.println(result);
	}
	
	public static String getJsonToData(String jsonString) {
		// Gson sGson=new Gson();
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization().create();
		JsonKdm map = null;
		try {
			map = gson.fromJson(jsonString, JsonKdm.class);
		} catch (Exception e) {
			// TODO: handle exception
			System.out.println("json数据转换失败！！！" + jsonString);
		}

		Iterator<Entry<String, HashMap<String, KdmData2>>> ite = map.entrySet().iterator();
		List<KdmData> kdmDataList = new ArrayList<KdmData>();
		while (ite.hasNext()) {
			KdmData kdmData = new KdmData();
			Entry<String, HashMap<String, KdmData2>> next = ite.next();
			String code = next.getKey();
			HashMap<String, KdmData2> hashMap = next.getValue();
			Iterator<Entry<String, KdmData2>> ite1 = hashMap.entrySet().iterator();
			kdmData.setCode(code);
			List<KdmData2> kdmData2List = new ArrayList<KdmData2>();
			while (ite1.hasNext()) {
				// KdmData2 kdmData2=new KdmData2();
				KdmData2 kdmData2 = ite1.next().getValue();
				kdmData2List.add(kdmData2);
			}
			kdmData.setData(kdmData2List);
			kdmData.setState("CODE_NOT_EXIST");
			kdmDataList.add(kdmData);
		}
		Gson sGson2 = new Gson();
		String result = sGson2.toJson(kdmDataList).toString();
		return result;
	}
	
	public static String putJsonDataHttp(String jsonData,String url) throws UnsupportedEncodingException {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		String res = null;
		try {
			HttpPost httpPost = new HttpPost(url);
			StringEntity se = new StringEntity(jsonData, Charset.forName("utf-8"));
			se.setContentType("application/json;charset=utf-8");
			httpPost.setEntity(se);
			CloseableHttpResponse response2 = httpclient.execute(httpPost);
			try {
				HttpEntity entity2 = response2.getEntity();
				res = EntityUtils.toString(entity2);
				if (!res.equals("{}")) {
					System.out.println("写入数据失败！"+res);
				}
			} finally {
				response2.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return res;
	}

}
