package cc.openxiot.wematrix.device.loach._sprinkler._fff;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.wematrix.device.loach._sprinkler._fff._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.wematrix.device.loach._sprinkler._fff._irrigationsystem.ServiceIrrigationSystem;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 洒水器
 */
public class DeviceFff extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:sprinkler:00000017:loach:fff:1";

    public DeviceFff() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "洒水器");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceIrrigationSystem.IID, new ServiceIrrigationSystem());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 灌溉系统
     */
    public ServiceIrrigationSystem _service_irrigation_system() {
        return (ServiceIrrigationSystem) services().get(ServiceIrrigationSystem.IID);
    }

}
