package cc.openxiot.matrix.device.loach._switch._k3;

import cn.geekcity.xiot.support.codegen.vertx.typedef.controller.DeviceController;

import cc.openxiot.matrix.device.loach._switch._k3._accessoryinformation.ServiceAccessoryInformation;
import cc.openxiot.matrix.device.loach._switch._k3._switch11.ServiceSwitch11;
import cc.openxiot.matrix.device.loach._switch._k3._switch12.ServiceSwitch12;
import cc.openxiot.matrix.device.loach._switch._k3._switch13.ServiceSwitch13;

import cn.geekcity.xiot.spec.definition.urn.DeviceType;

/**
 * 三键开关
 */
public class DeviceK3 extends DeviceController {

    public static final String TYPE = "urn:homekit-spec:device:switch:00000008:loach:k3:1";

    public DeviceK3() {
        super(new DeviceType(TYPE));

        super.description().put("zh-CN", "三键开关");

        super.services().put(ServiceAccessoryInformation.IID, new ServiceAccessoryInformation());
        super.services().put(ServiceSwitch11.IID, new ServiceSwitch11());
        super.services().put(ServiceSwitch12.IID, new ServiceSwitch12());
        super.services().put(ServiceSwitch13.IID, new ServiceSwitch13());
    }

    /**
     * 配件信息
     */
    public ServiceAccessoryInformation _service_accessory_information() {
        return (ServiceAccessoryInformation) services().get(ServiceAccessoryInformation.IID);
    }

    /**
     * 左键
     */
    public ServiceSwitch11 _service_switch11() {
        return (ServiceSwitch11) services().get(ServiceSwitch11.IID);
    }

    /**
     * 右键
     */
    public ServiceSwitch12 _service_switch12() {
        return (ServiceSwitch12) services().get(ServiceSwitch12.IID);
    }

    /**
     * Middle Switch
     */
    public ServiceSwitch13 _service_switch13() {
        return (ServiceSwitch13) services().get(ServiceSwitch13.IID);
    }

}
