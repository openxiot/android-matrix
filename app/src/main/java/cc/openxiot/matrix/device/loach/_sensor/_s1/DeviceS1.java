package cc.openxiot.matrix.device.loach._sensor._s1;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.matrix.device.loach._sensor._s1._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.matrix.device.loach._sensor._s1._temperaturesensor.ServiceTemperatureSensor;
import cc.openxiot.matrix.device.loach._sensor._s1._humiditysensor.ServiceHumiditySensor;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 温湿度传感器
 */
public class DeviceS1 extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:sensor:0000000a:loach:s1:1";

    public DeviceS1() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "温湿度传感器");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceTemperatureSensor.IID, new ServiceTemperatureSensor());
        super.services().put(ServiceHumiditySensor.IID, new ServiceHumiditySensor());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 温度传感器
     */
    public ServiceTemperatureSensor _service_temperature_sensor() {
        return (ServiceTemperatureSensor) services().get(ServiceTemperatureSensor.IID);
    }

    /**
     * 湿度传感器
     */
    public ServiceHumiditySensor _service_humidity_sensor() {
        return (ServiceHumiditySensor) services().get(ServiceHumiditySensor.IID);
    }

}
