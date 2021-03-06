package cn.getcube.develop.service.Impl;

import cn.getcube.develop.dao.developes.CertifiedDao;
import cn.getcube.develop.entity.CertifiedEntity;
import cn.getcube.develop.service.CertifiedService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Created by Administrator on 2016/3/14.
 */
@Service
public class CertifiedServiceImpl implements CertifiedService {

    @Resource
    private CertifiedDao certifiedDao;

    @Override
    public CertifiedEntity queryCertified(int userId) {
        return certifiedDao.queryByUserId(userId);
    }

    @Override
    public void saveCertified(CertifiedEntity certified) {
        CertifiedEntity certifiedEntity = certifiedDao.queryByUserId(certified.getUserId());

        certifiedDao.saveCertified(certified);
    }

    @Override
    public int savePersonal(CertifiedEntity certified) {
        return certifiedDao.savePersonal(certified);
    }

    @Override
    public int updatePersonal(CertifiedEntity certified) {
        return certifiedDao.updatePersonal(certified);
    }

    @Override
    public CertifiedEntity queryByUserId(Integer userId) {
        return certifiedDao.queryByUserId(userId);
    }

}
