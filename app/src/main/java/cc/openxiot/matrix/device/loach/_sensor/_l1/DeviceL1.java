package cc.openxiot.matrix.device.loach._sensor._l1;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.matrix.device.loach._sensor._l1._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.matrix.device.loach._sensor._l1._lightsensor.ServiceLightSensor;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 光线传感器
 */
public class DeviceL1 extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:sensor:0000000a:loach:l1:1";

    public DeviceL1() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "光线传感器");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceLightSensor.IID, new ServiceLightSensor());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 光线传感器
     */
    public ServiceLightSensor _service_light_sensor() {
        return (ServiceLightSensor) services().get(ServiceLightSensor.IID);
    }

}
