package cc.openxiot.wematrix.device.loach._airconditioner._g1;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.wematrix.device.loach._airconditioner._g1._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.wematrix.device.loach._airconditioner._g1._heatercooler.ServiceHeaterCooler;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 空调
 */
public class DeviceG1 extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:air-conditioner:00000014:loach:g1:1";

    public DeviceG1() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "空调");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceHeaterCooler.IID, new ServiceHeaterCooler());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 加热器/冷却器
     */
    public ServiceHeaterCooler _service_heater_cooler() {
        return (ServiceHeaterCooler) services().get(ServiceHeaterCooler.IID);
    }

}
