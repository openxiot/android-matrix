package cc.openxiot.matrix.device.loach._sensor._co2;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.matrix.device.loach._sensor._co2._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.matrix.device.loach._sensor._co2._carbondioxidesensor.ServiceCarbonDioxideSensor;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 二氧化碳传感器
 */
public class DeviceCo2 extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:sensor:0000000a:loach:co2:1";

    public DeviceCo2() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "二氧化碳传感器");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceCarbonDioxideSensor.IID, new ServiceCarbonDioxideSensor());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 二氧化碳传感器
     */
    public ServiceCarbonDioxideSensor _service_carbon_dioxide_sensor() {
        return (ServiceCarbonDioxideSensor) services().get(ServiceCarbonDioxideSensor.IID);
    }

}
