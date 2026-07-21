# 智能场地



## 一、产品

* 产品列表

    通过以下接口可以读到产品列表：

    https://product.openxiot.cn/v1/product/basic/public

* 产品功能定义

    通过一下接口可以读到产品的具体实例定义，productId是产品ID

    https://product.openxiot.cn/v1/product/instance/many?productId=6a57251ebd3f2b003984133a

* 产品功能定义的描述，参考这个文档：

    /Users/ouyang/workshop/openxiot/service/webapp-docs/docs/spec/spec.mdx



## 二、服务

* 主服务

    主服务的源码在这里：

    /Users/ouyang/workshop/openxiot/service/service-site

* 接口协议里用到的一些定义，比如读属性、写属性、执行方法等，可以参考这个文档里的概念：

    /Users/ouyang/workshop/openxiot/service/webapp-docs/docs/spec/xcp.mdx

* 登录服务

    登录服务的源码在这里：

    /Users/ouyang/workshop/openxiot/service/service-account



## 三、账号和组织

用户使用github账号登录，可以创建组织，可以添加某个用户为组织的成员。

组织里的所有成员都可以看到组织里的项目。

一个用户可能在多个组织里。

一个组织下可能有多个项目。



测试的话，可以不用登录，直接用这个token:

```
eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tL2lzc3VlciIsInVwbiI6IjZhNGRhZmU1YTE3Nzg2ZGJlMDEyOTlhYyIsInVzZXJuYW1lIjoiZ2tjaXR5IiwiZ3JvdXBzIjpbImRldmVsb3BlciJdLCJiaXJ0aGRhdGUiOiJGcmkgSnVsIDE3IDAyOjE2OjAyIEdNVCAyMDI2IiwiZXhwIjoxNzg0ODU5MzYyLCJpYXQiOjE3ODQyNTQ1NjIsImp0aSI6ImRlM2NhMTRhLTkwMzUtNGQ3Zi05ZTJhLTM1MTg3YmIxMmYyNSJ9.Tw3RkfVCchzEDYQQs9pckQKsF6OoZZtXU6FbZYeNBc3McurOeKLKVQrOcH-usNEvJYgcbx-U1zoCREE9kdd0yylJmUQuopVX7gBCnCZU8-7dCZLQ6qYzhGLGOV-1GBIi3oE_PpiJxkBqYyW1diBesyo6aoAbgoVToX5hGJPjrHFnXDAyboZRX8wHsimSrAR98RyDwRTnnXMFhez0OPS4-y2FolN14dDB_0xN0vtzx1S4NGbbVq5F2f1pXIchanlUmHBb4SSDoeCp19QB7HnPZbx8U4qv-wVahvNg57R5EODsTLBZMKARUIfCYkAfdEhMbWBqvXjwpgetIbwf4KLNyw
```



## 四、项目

用户登录后，可以创建、删除、修改、查看自己的项目。

项目就是 service-site 里的 根空间，在这个根空间里，可以创建一个项目的空间树，比如：

* 楼栋

* 楼层

* 房间

* ……

    

权限管理：

* 管理员：修改项目、删除项目。
* 其他人：浏览项目


## 五、添加设备
在调用主服务的添加设备接口，就可以将设备添加到项目中，并且放到某个空间下。
设备的ID和对应的accessKey，可以通过扫描二维码得到。


## 六、目标

使用 compose 构造一个智能场地的android应用。

1、界面酷炫，可以切换深色主题和浅色主题

2、用户登录后，可以浏览自己所在的组织列表，可以切换当前组织。

3、在当前组织下，可以浏览当前组织的项目列表，可以切换当前项目。

4、再当前项目下，可以管理项目的空间结构。

5、界面设计风格，学习小米的米家app，或者学习微信。
