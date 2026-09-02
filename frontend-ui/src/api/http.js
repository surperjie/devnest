import axios from "axios";

// 统一 HTTP 客户端,调 SpringBoot 127.0.0.1:8080
// 响应拦截器解包 ApiResult,成功返回 data.data,失败 reject Error(msg)
const http = axios.create({
  baseURL: "http://127.0.0.1:8080/api",
  timeout: 15000,
});

http.interceptors.response.use(
  (res) => {
    const body = res.data;
    if (body && typeof body.code !== "undefined") {
      if (body.code === 0) {
        return body.data;
      }
      return Promise.reject(new Error(body.msg || "请求失败"));
    }
    return body;
  },
  (err) => {
    const msg = err.response?.data?.msg || err.message || "网络异常";
    return Promise.reject(new Error(msg));
  }
);

export default http;
