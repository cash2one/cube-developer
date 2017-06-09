package cn.getcube.develop.controller;

import cn.getcube.develop.AuthConstants;
import cn.getcube.develop.StateCode;
import cn.getcube.develop.anaotation.TokenVerify;
import cn.getcube.develop.dao.developes.UserDao;
import cn.getcube.develop.entity.UserEntity;
import cn.getcube.develop.service.UserService;
import cn.getcube.develop.utils.*;
import cn.getcube.develop.utils.redis.RedisKey;
import cn.getcube.develop.utils.redis.UpdateUserRedis;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.context.annotation.Scope;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.JedisCluster;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.apache.coyote.http11.Constants.a;

/**
 * Created by SubDong on 2016/3/8.
 */
@RestController
@Scope("prototype")
@RequestMapping(value = "/user")
public class UserController {

    @Resource
    JedisCluster jc;
    @Resource
    private UserService userService;

    @Resource
    private UserDao userDao;

    /**
     * 注册账号
     *
     * @return
     */
    @RequestMapping(value = "/register", method = RequestMethod.POST)
    public DataResult<UserEntity> product(@RequestParam(name = "name", required = true) String name,
                                          @RequestParam(name = "account", required = true) String account,
                                          @RequestParam(name = "password", required = true) String password,
                                          @RequestParam(name = "userType", required = false) Integer userType,
                                          @RequestParam(name = "way", required = false) Integer way) {
        DataResult<UserEntity> result = new DataResult<>();
        if (name != null && account != null && password != null) {

            UserEntity userEntity = new UserEntity();

            if (account.contains("@")) {
                userEntity.setEmail(account);
                //邮箱验证
                if (!RegexUtil.isEmail(userEntity.getEmail())) {
                    return new DataResult<>(StateCode.AUTH_ERROR_10004.getCode(), AuthConstants.FORMAT_ERROR);
                }
                UserEntity param = new UserEntity();
                param.setEmail(account);
                int count = userService.queryExists(param);
                if (count != 0) {
                    return new DataResult<>(StateCode.AUTH_ERROR_10023.getCode(), AuthConstants.EMAIL_EXISTS);
                }
            } else {
                userEntity.setPhone(account);
                if (!RegexUtil.checkMobile(userEntity.getPhone())) {
                    return new DataResult<>(StateCode.AUTH_ERROR_10022.getCode(), AuthConstants.PHONE_FORMAT_ERROR);
                }

                UserEntity param = new UserEntity();
                param.setPhone(account);
                int count = userService.queryExists(param);
                if (count != 0) {
                    return new DataResult<>(StateCode.AUTH_ERROR_10024.getCode(), AuthConstants.PHONE_EXISTS);
                }
            }

            //MD5加密
            MD5 md5 = new MD5.Builder().source(password).salt(AuthConstants.USER_SALT).build();
            userEntity.setName(name);
            userEntity.setPassword(md5.getMD5());
            userEntity.setUsertype(null==userType?1:userType);
            if (way != null) {
                userEntity.setWay(way);
            }
            userEntity.setCreate_time(new Date());
            userEntity.setUpdate_time(new Date());

            try {
                UserEntity user = userService.queryUser(userEntity);
                if( user != null){
                    userEntity.setId(user.getId());
                    userService.updateUser(userEntity);
                }else {
                    userService.addUser(userEntity);
                }

                MessageUtils.getInstance().sendEmailOrPhone(jc, account, userEntity);
                return new DataResult<>(userEntity);
            } catch (Exception e) {
                return new DataResult<>(StateCode.AUTH_ERROR_10009, AuthConstants.REGISTER_ERROR);
            }
        }
        return new DataResult<>(StateCode.AUTH_ERROR_10009, AuthConstants.REGISTER_ERROR);
    }

    /**
     * 多条件查询用户
     *
     * @return
     */
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public DataResult<JSONObject> product(@RequestParam(name = "version", required = false) String version,
                                          @RequestParam(name = "id", required = false) Integer id,
                                          @RequestParam(name = "name", required = false) String name,
                                          @RequestParam(name = "email", required = false) String email,
                                          @RequestParam(name = "phone", required = false) String phone) {
        UserEntity userEntity = new UserEntity();
        if(id != null){
            userEntity.setId(id);
        }
        if(name != null && !name.isEmpty()){
            userEntity.setName(name);
        }
        if(email != null && !email.isEmpty()){
            userEntity.setEmail(email);
        }
        if(phone != null && !phone.isEmpty()){
            userEntity.setPhone(phone);
        }

        List<UserEntity> users = userService.queryUsers(userEntity);
        if (Objects.isNull(users)) {
            return new DataResult<>(StateCode.AUTH_ERROR_10008.getCode(),AuthConstants.QUERY_NO_DATA);
        }
       JSONObject jsonObject = new JSONObject();
        jsonObject.put("users",users);
        return new DataResult<JSONObject>(jsonObject);

    }

    @RequestMapping(value = "/query_token", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult queryByToken(@RequestParam(name = "token", required = false) String token,
                                          UserEntity userSession) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userSession.getId());

        UserEntity user = userService.queryUser(userEntity);
        if (Objects.isNull(user)) {
            return BaseResult.build(StateCode.AUTH_ERROR_10008.getCode(),AuthConstants.QUERY_NO_DATA);
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("user",user);
        return new DataResult<JSONObject>(jsonObject);

    }

    /**
     * 登陆
     *
     * @return
     */
    @RequestMapping(value = "/login", method = RequestMethod.POST)
    public DataResult<JSONObject> signin(@RequestParam(name = "version", required = false) String version,
                                         @RequestParam(name = "targetUrl", required = false) String targetUrl,
                                         @RequestParam(name = "username", required = true) String username,
                                         @RequestParam(name = "password", required = true) String password) throws UnsupportedEncodingException {
        UserEntity userEntity = new UserEntity();
        if (username == null || username.isEmpty()) {
            return new DataResult<>(StateCode.AUTH_ERROR_10016.getCode(), AuthConstants.NULL_NAME);
        }

        if (password == null || password.isEmpty()) {
            return new DataResult<>(StateCode.AUTH_ERROR_10016.getCode(), AuthConstants.NULL_PASSWORD);
        }

        if (username != null && username.indexOf("@") != -1) {
            userEntity.setEmail(username);
        } else {
            userEntity.setPhone(username);
        }

        MD5 md5 = new MD5.Builder().source(password).salt(AuthConstants.USER_SALT).build();
        userEntity.setPassword(md5.getMD5());
        userEntity = userService.login(userEntity);
        if (userEntity == null) {
            return new DataResult<>(StateCode.AUTH_ERROR_10002.getCode(), AuthConstants.USER_PSD_ERROR);
        } else {
            if(userService.queryExists(userEntity) == 0){
                return new DataResult<>(StateCode.AUTH_ERROR_9997.getCode(), AuthConstants.ACTIVATION_FAILED);
            }
//            map.put("code", "0000");
//            map.put("data", targetUrl);
            //TODO  此处加 tn_ 标注token 特殊性，后期优化删除，现在不动
            String uuid = "tn_" + UUID.randomUUID().toString().replaceAll("-", "");
            jc.set("token"+userEntity.getId(), uuid);

            UserEntity userEntity1 = userService.queryUser(userEntity);
            jc.set(uuid, JSON.toJSONString(userEntity1));
//            if (4 == userEntity.getUsertype()) {
//                map.put("code", "301");
//            }
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("token", uuid);
            jsonObject.put("user", userEntity);

            DataResult<JSONObject> dataResult = new DataResult<>();
            dataResult.setData(jsonObject);
            dataResult.setCode(StateCode.Ok.getCode());
            dataResult.setDesc(AuthConstants.MSG_OK);

            return dataResult;

        }

    }


    @RequestMapping(value = "/logout", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult logout(@RequestParam(name = "token", required = true) String token,
                               @RequestParam(name = "version", required = false) String version,
                               UserEntity userSession) {
        try {
            jc.del(token);
            jc.del("token"+userSession.getId());
            return new BaseResult();
        } catch (Exception e) {
            return new BaseResult(StateCode.AUTH_ERROR_10025.getCode(),AuthConstants.LOGOUT_ERROR);
        }

    }

    /**
     * 修改账户名称
     *
     * @param token
     * @param version
     * @return
     */
    @RequestMapping(value = "/update/name", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult updateUserName(@RequestParam(name = "token", required = true) String token,
                                                  @RequestParam(name = "name", required = true) String name,
                                                  @RequestParam(name = "version", required = false) String version,
                                                 UserEntity userSession) {
        UserEntity userEntity = new UserEntity();
        userEntity.setId(userSession.getId());
        userEntity.setName(name);
        userEntity.setUpdate_time(new Date());
        int updateUser = userService.updateUser(userEntity);
        if (updateUser > 0) {
            //更新缓存
            UpdateUserRedis.updateUser(jc,userSession.getId(),token,userDao);
            UserEntity user = userDao.queryUser(userEntity);
            return new DataResult<>(user);
        } else {
            return new DataResult<UserEntity>(StateCode.AUTH_ERROR_10017.getCode(),AuthConstants.UPDATE_ERROR);
        }
    }


    /**
     * 手机号注册 激活
     *
     * @param msmCode
     * @param version
     * @return
     */
    @RequestMapping(value = "/phone/activation", method = RequestMethod.POST)
    public BaseResult active(@RequestParam(name = "phone", required = true) String phone,
                          @RequestParam(name = "msmCode", required = true) String msmCode,
                          @RequestParam(name = "version", required = false) String version) {

        String codeKey = jc.get(RedisKey.SMS_REG+phone);
        if (codeKey != null && !codeKey.equals("")) {
            if ((msmCode.toLowerCase()).equals(codeKey.toLowerCase())) {

                UserEntity temp = new UserEntity();
                temp.setPhone(phone);
                UserEntity db = userService.queryUser(temp);

                if(null==db){
                    return new BaseResult(StateCode.AUTH_ERROR_10021,"用户不存在");
                }else if(db.getActivation()==1){
                    return new BaseResult(StateCode.AUTH_ERROR_10024,"手机号已被注册");
                }

                UserEntity userEntity = new UserEntity();
                userEntity.setId(db.getId());
                userEntity.setPhone_verify(1);
                userEntity.setActivation(1);
                userEntity.setUpdate_time(new Date());
                int updateUser = userService.updateUser(userEntity);
                if (updateUser > 0) {
                    jc.del(RedisKey.SMS_REG+phone);
                    return new BaseResult(AuthConstants.MSG_OK);
                } else {
                    return new BaseResult(StateCode.Unknown.getCode(),"未知错误");
                }
            } else {
                return new BaseResult(StateCode.AUTH_ERROR_10018,AuthConstants.VERIFY_FAILED);
            }
        } else {
            return new BaseResult(StateCode.AUTH_ERROR_10012,AuthConstants.VERIFY_EXPIRE);
        }

    }


    /**
     * 手机绑定
     *
     * @param token
     * @param msmCode
     * @param version
     * @return
     */
    @RequestMapping(value = "/phone/verify", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult ver(@RequestParam(name = "token", required = true) String token,
                              @RequestParam(name = "phone", required = true) String phone,
                              @RequestParam(name = "msmCode", required = true) String msmCode,
                              @RequestParam(name = "version", required = false) String version,
                              UserEntity userSession) {

        String codeKey = jc.get(RedisKey.SMS_BIND+phone);
        if (codeKey != null && !codeKey.equals("")) {
            if ((msmCode.toLowerCase()).equals(codeKey.toLowerCase())) {

                if(!Objects.isNull(userSession.getPhone())){
                    return new BaseResult(StateCode.AUTH_ERROR_10033,"已绑定手机");
                }
                UserEntity temp = new UserEntity();
                temp.setPhone(phone);
                UserEntity db = userService.queryUser(temp);

                if(null!=db&&db.getActivation()==1){
                    return new BaseResult(StateCode.AUTH_ERROR_10024,AuthConstants.PHONE_EXISTS);
                }

                UserEntity userEntity = new UserEntity();
                userEntity.setId(userSession.getId());
                userEntity.setPhone(phone);
                userEntity.setPhone_verify(1);
                userEntity.setUpdate_time(new Date());
                int updateUser = userService.updateUser(userEntity);
                if (updateUser > 0) {
                    jc.del(RedisKey.SMS_BIND+phone);

                    //更新缓存
                    UpdateUserRedis.updateUser(jc,userSession.getId(),token,userDao);

                    return new BaseResult(AuthConstants.MSG_OK);
                } else {
                    return new BaseResult(StateCode.AUTH_ERROR_10017,AuthConstants.PHONE_BINDING_ERROR);
                }
            } else {
                return new BaseResult(StateCode.AUTH_ERROR_10018,AuthConstants.VERIFY_FAILED);
            }
        } else {
            return new BaseResult(StateCode.AUTH_ERROR_10012,AuthConstants.VERIFY_EXPIRE);
        }

    }


    /**
     * 手机绑定修改
     *
     * @param token
     * @param msmCode
     * @param version
     * @return
     */
    @RequestMapping(value = "/phone/fix", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult param(@RequestParam(name = "token", required = true) String token,
                            @RequestParam(name = "phone", required = true) String phone,
                            @RequestParam(name = "msmCode", required = true) String msmCode,
                            @RequestParam(name = "version", required = false) String version,
                            UserEntity userSession) {

        String codeKey = jc.get(RedisKey.SMS_FIX_NEW+phone);
        if (codeKey != null && !codeKey.equals("")) {
            if ((msmCode.toLowerCase()).equals(codeKey.toLowerCase())) {

                if(Objects.isNull(userSession.getPhone())){
                    return new BaseResult(StateCode.AUTH_ERROR_10031,"未绑定手机");
                }

                UserEntity temp = new UserEntity();
                temp.setPhone(phone);
                UserEntity db = userService.queryUser(temp);

                if(null!=db&&db.getActivation()==1){
                    return new BaseResult(StateCode.AUTH_ERROR_10024,AuthConstants.PHONE_EXISTS);
                }
                UserEntity userEntity = new UserEntity();
                userEntity.setId(userSession.getId());
                userEntity.setPhone(phone);
                userEntity.setPhone_verify(1);
                userEntity.setUpdate_time(new Date());
                int updateUser = userService.fixPhone(userEntity);
                if (updateUser > 0) {
                    jc.del(RedisKey.SMS_FIX_NEW+phone);

                    //更新缓存
                    UpdateUserRedis.updateUser(jc,userSession.getId(),token,userDao);

                    return new BaseResult(AuthConstants.MSG_OK);
                } else {
                    return new BaseResult(StateCode.AUTH_ERROR_10017,AuthConstants.PHONE_BINDING_ERROR);
                }
            } else {
                return new BaseResult(StateCode.AUTH_ERROR_10018,AuthConstants.VERIFY_FAILED);
            }
        } else {
            return new BaseResult(StateCode.AUTH_ERROR_10012,AuthConstants.VERIFY_EXPIRE);
        }

    }


    /**
     * 解绑手机号
     *
     * @param token
     * @param msmCode
     * @param version
     * @return
     */
    @RequestMapping(value = "/phone/unbind", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult unbind(@RequestParam(name = "token", required = true) String token,
                            @RequestParam(name = "msmCode", required = true) String msmCode,
                            @RequestParam(name = "version", required = false) String version,
                            UserEntity userSession) {

        String codeKey = jc.get(RedisKey.SMS_UNBIND+userSession.getPhone());
        if (codeKey != null && !codeKey.equals("")) {
            if ((msmCode.toLowerCase()).equals(codeKey.toLowerCase())) {
                if(Objects.isNull(userSession.getPhone())){
                    return new BaseResult(StateCode.AUTH_ERROR_10031,"未绑定手机");
                }

                UserEntity temp = new UserEntity();
                temp.setId(userSession.getId());
                UserEntity db = userService.queryUser(temp);

                if(Objects.isNull(db.getEmail())){
                    return new BaseResult(StateCode.AUTH_ERROR_10028,"未绑定邮箱，不能解绑手机");
                }
                UserEntity userEntity = new UserEntity();
                userEntity.setId(userSession.getId());
                userEntity.setPhone(null);
                userEntity.setPhone_verify(0);
                userEntity.setUpdate_time(new Date());
                int updateUser = userService.fixPhone(userEntity);
                if (updateUser > 0) {
                    jc.del(RedisKey.SMS_UNBIND+userSession.getPhone());

                    //更新缓存
                    UpdateUserRedis.updateUser(jc,userSession.getId(),token,userDao);
                    return new BaseResult(AuthConstants.MSG_OK);
                } else {
                    return new BaseResult(StateCode.AUTH_ERROR_10017,AuthConstants.PHONE_UNBINDING_ERROR);
                }
            } else {
                return new BaseResult(StateCode.AUTH_ERROR_10018,AuthConstants.VERIFY_FAILED);
            }
        } else {
            return new BaseResult(StateCode.AUTH_ERROR_10012,AuthConstants.VERIFY_EXPIRE);
        }

    }

    /**
     * 手机号找回密码
     *
     * @param msmCode
     * @param version
     * @return
     */
    @RequestMapping(value = "/phone/password", method = RequestMethod.POST)
    public BaseResult password(@RequestParam(name = "msmCode", required = true) String msmCode,
                               @RequestParam(name = "phone", required = true) String phone,
                               @RequestParam(name = "password", required = true) String password,
                              @RequestParam(name = "version", required = false) String version) {

        String codeKey = jc.get(RedisKey.SMS_RESET+phone);
        if (codeKey != null && !codeKey.equals("")) {
            if ((msmCode.toLowerCase()).equals(codeKey.toLowerCase())) {

                UserEntity temp = new UserEntity();
                temp.setPhone(phone);
                UserEntity db = userService.queryUser(temp);

                if(Objects.isNull(db)){
                    return new BaseResult(StateCode.AUTH_ERROR_10021,"用户不存在");
                }

                //没有激活不能找回密码
                if (db.getActivation()==0) {
                    return BaseResult.build(StateCode.AUTH_ERROR_10034, "账号未激活");
                }

                MD5 md5 = new MD5.Builder().source(password).salt(AuthConstants.USER_SALT).build();
                UserEntity userEntity = new UserEntity();
                userEntity.setId(db.getId());
                userEntity.setPassword(md5.getMD5());
                userEntity.setUpdate_time(new Date());
                int updateUser = userService.updateUser(userEntity);
                if (updateUser > 0) {
                    jc.del(RedisKey.SMS_RESET+phone);
                    return new BaseResult(AuthConstants.MSG_OK);
                } else {
                    return new BaseResult(StateCode.Unknown,"未知错误");
                }
            } else {
                return new BaseResult(StateCode.AUTH_ERROR_10018,AuthConstants.VERIFY_FAILED);
            }
        } else {
            return new BaseResult(StateCode.AUTH_ERROR_10012,AuthConstants.VERIFY_EXPIRE);
        }

    }



    /**
     * 头像上传
     * @param token
     * @param version
     * @return
     */
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    @TokenVerify
    public BaseResult upload(
                               @RequestParam(name = "token", required = true) String token,
                               @RequestParam(name = "file", required = true) MultipartFile file,
                               @RequestParam(name = "version", required = false) String version,
                                UserEntity userSession) {
            String avatarUrl = FileUploadUtils.uploadFile(file, 1);
            UserEntity userEntity = new UserEntity();
            userEntity.setUpdate_time(new Date());
            userEntity.setAvatar(avatarUrl);
            userEntity.setId(userSession.getId());
            int updateUser = userService.updateUser(userEntity);
            if (updateUser > 0) {
                UserEntity user = userService.queryUser(userEntity);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("user",user);

                //更新缓存
                UpdateUserRedis.updateUser(jc,userSession.getId(),token,userDao);

                return new DataResult<>(jsonObject);
            } else {
                return new DataResult<>(StateCode.AUTH_ERROR_10017,AuthConstants.FACE_UPLOAD);
            }
    }

}
