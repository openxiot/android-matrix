package cc.openxiot.wematrix.device.loach._sensor._sm1;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.wematrix.device.loach._sensor._sm1._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.wematrix.device.loach._sensor._sm1._smokesensor.ServiceSmokeSensor;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 烟雾传感器
 */
public class DeviceSm1 extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:sensor:0000000a:loach:sm1:1";

    public DeviceSm1() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "烟雾传感器");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceSmokeSensor.IID, new ServiceSmokeSensor());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 烟雾传感器
     */
    public ServiceSmokeSensor _service_smoke_sensor() {
        return (ServiceSmokeSensor) services().get(ServiceSmokeSensor.IID);
    }

}
