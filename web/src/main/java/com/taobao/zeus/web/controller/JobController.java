package com.taobao.zeus.web.controller;

import com.taobao.zeus.dal.logic.*;
import com.taobao.zeus.dal.logic.impl.ReadOnlyGroupManagerWithJob;
import com.taobao.zeus.dal.model.ZeusHostGroup;
import com.taobao.zeus.dal.model.ZeusUser;
import com.taobao.zeus.dal.tool.GroupBean;
import com.taobao.zeus.dal.tool.JobBean;
import com.taobao.zeus.model.JobDescriptor;
import com.taobao.zeus.model.ZeusFollow;
import com.taobao.zeus.model.processer.Processer;
import com.taobao.zeus.util.DateUtil;
import com.taobao.zeus.web.controller.response.CommonResponse;
import com.taobao.zeus.web.controller.response.ReturnCode;
import com.taobao.zeus.web.platform.client.module.jobmanager.JobModel;
import com.taobao.zeus.web.platform.client.util.ZUser;
import com.taobao.zeus.web.platform.client.util.ZUserContactTuple;
import com.taobao.zeus.web.util.LoginUser;
import com.taobao.zeus.web.util.PermissionGroupManagerWithJob;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/job")
public class JobController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    @Autowired
    private ReadOnlyGroupManagerWithJob readOnlyGroupManagerWithJob;
    @Autowired
    private FollowManagerWithJob followManagerWithJob;
    @Autowired
    private JobHistoryManager jobHistoryManager;

    @Autowired
    private PermissionGroupManagerWithJob permissionGroupManagerWithJob;
    @Autowired
    private FollowManagerWithJob followManager;
    @Autowired
    private UserManager userManager;
    @Autowired
    private PermissionManager permissionManager;

    @Autowired
    private HostGroupManager hostGroupManager;

    @RequestMapping(value = "/get_upstream_job", method = RequestMethod.GET)
    public CommonResponse<JobModel> getUpstreamJob(@RequestParam(value = "jobId") String jobId) throws Exception {
        try {
            JobBean jobBean = permissionGroupManagerWithJob.getUpstreamJobBean(jobId);
            JobModel jobModel = new JobModel();

            jobModel.setCronExpression(jobBean.getJobDescriptor()
                    .getCronExpression());
            jobModel.setDependencies(jobBean.getJobDescriptor().getDependencies());
            jobModel.setDesc(jobBean.getJobDescriptor().getDesc());
            jobModel.setGroupId(jobBean.getJobDescriptor().getGroupId());
            jobModel.setId(jobBean.getJobDescriptor().getId());
            String jobRunType = null;
            if (jobBean.getJobDescriptor().getJobType() == JobDescriptor.JobRunType.MapReduce) {
                jobRunType = JobModel.MapReduce;
            } else if (jobBean.getJobDescriptor().getJobType() == JobDescriptor.JobRunType.Shell) {
                jobRunType = JobModel.SHELL;
            } else if (jobBean.getJobDescriptor().getJobType() == JobDescriptor.JobRunType.Hive) {
                jobRunType = JobModel.HIVE;
            }
            jobModel.setJobRunType(jobRunType);
            String jobScheduleType = null;
            if (jobBean.getJobDescriptor().getScheduleType() == JobDescriptor.JobScheduleType.Dependent) {
                jobScheduleType = JobModel.DEPEND_JOB;
            }
            if (jobBean.getJobDescriptor().getScheduleType() == JobDescriptor.JobScheduleType.Independent) {
                jobScheduleType = JobModel.INDEPEN_JOB;
            }
            if (jobBean.getJobDescriptor().getScheduleType() == JobDescriptor.JobScheduleType.CyleJob) {
                jobScheduleType = JobModel.CYCLE_JOB;
            }
            jobModel.setJobScheduleType(jobScheduleType);
            jobModel.setLocalProperties(jobBean.getJobDescriptor().getProperties());
            jobModel.setName(jobBean.getJobDescriptor().getName());
            jobModel.setOwner(jobBean.getJobDescriptor().getOwner());
            String ownerName = userManager.findByUid(jobModel.getOwner()).getName();
            if (ownerName == null || "".equals(ownerName.trim())
                    || "null".equals(ownerName)) {
                ownerName = jobModel.getOwner();
            }
            jobModel.setOwnerName(ownerName);
            jobModel.setLocalResources(jobBean.getJobDescriptor().getResources());
            jobModel.setAllProperties(jobBean.getHierarchyProperties()
                    .getAllProperties());
            jobModel.setAllResources(jobBean.getHierarchyResources());

            jobModel.setAuto(jobBean.getJobDescriptor().getAuto());
            jobModel.setScript(jobBean.getJobDescriptor().getScript());

            List<String> preList = new ArrayList<String>();
            if (!jobBean.getJobDescriptor().getPreProcessers().isEmpty()) {
                for (Processer p : jobBean.getJobDescriptor().getPreProcessers()) {
                    JSONObject o = new JSONObject();
                    o.put("id", p.getId());
                    o.put("config", p.getConfig());
                    preList.add(o.toString());
                }
            }
            jobModel.setPreProcessers(preList);

            List<String> postList = new ArrayList<String>();
            if (!jobBean.getJobDescriptor().getPostProcessers().isEmpty()) {
                for (Processer p : jobBean.getJobDescriptor().getPostProcessers()) {
                    JSONObject o = new JSONObject();
                    o.put("id", p.getId());
                    o.put("config", p.getConfig());
                    postList.add(o.toString());
                }
            }
            jobModel.setPostProcessers(postList);

            jobModel.setAdmin(permissionGroupManagerWithJob.hasJobPermission(LoginUser
                    .getUser().getUid(), jobId));

            List<ZeusFollow> follows = followManager.findJobFollowers(jobId);
            if (follows != null) {
                List<String> followNames = new ArrayList<String>();
                for (ZeusFollow zf : follows) {
                    String name = userManager.findByUid(zf.getUid()).getName();
                    if (name == null || "".equals(name.trim())) {
                        name = zf.getUid();
                    }
                    followNames.add(name);
                }
                jobModel.setFollows(followNames);
            }
            jobModel.setImportantContacts(getImportantContactUid(jobId));
            List<String> ladmins = permissionManager.getJobAdmins(jobId);
            List<String> admins = new ArrayList<String>();
            for (String s : ladmins) {
                String name = userManager.findByUid(s).getName();
                if (name == null || "".equals(name.trim()) || "null".equals(name)) {
                    name = s;
                }
                admins.add(name);
            }
            jobModel.setAdmins(admins);


            List<String> owners = new ArrayList<String>();
            owners.add(jobBean.getJobDescriptor().getOwner());
            GroupBean parent = jobBean.getGroupBean();
            while (parent != null) {
                if (!owners.contains(parent.getGroupDescriptor().getOwner())) {
                    owners.add(parent.getGroupDescriptor().getOwner());
                }
                parent = parent.getParentGroupBean();
            }
            jobModel.setOwners(owners);

            // 所有secret. 开头的配置项都进行权限控制
            for (String key : jobModel.getAllProperties().keySet()) {
                boolean isLocal = jobModel.getLocalProperties().get(key) == null ? false
                        : true;
                if (key.startsWith("secret.")) {
                    if (!isLocal) {
                        jobModel.getAllProperties().put(key, "*");
                    } else {
                        if (!jobModel.getAdmin()
                                && !jobModel.getOwner().equals(
                                LoginUser.getUser().getUid())) {
                            jobModel.getLocalProperties().put(key, "*");
                        }
                    }
                }
            }
            // 本地配置项中的hadoop.hadoop.job.ugi 只有管理员和owner才能查看，继承配置项不能查看
            String SecretKey = "core-site.hadoop.job.ugi";
            if (jobModel.getLocalProperties().containsKey(SecretKey)) {
                String value = jobModel.getLocalProperties().get(SecretKey);
                if (value.lastIndexOf("#") == -1) {
                    value = "*";
                } else {
                    value = value.substring(0, value.lastIndexOf("#"));
                    value += "#*";
                }
                if (!jobModel.getAdmin()
                        && !jobModel.getOwner()
                        .equals(LoginUser.getUser().getUid())) {
                    jobModel.getLocalProperties().put(SecretKey, value);
                }
                jobModel.getAllProperties().put(SecretKey, value);
            } else if (jobModel.getAllProperties().containsKey(SecretKey)) {
                String value = jobModel.getAllProperties().get(SecretKey);
                if (value.lastIndexOf("#") == -1) {
                    value = "*";
                } else {
                    value = value.substring(0, value.lastIndexOf("#"));
                    value += "#*";
                }
                jobModel.getAllProperties().put(SecretKey, value);
            }
            // 如果zeus.secret.script=true 并且没有权限，对script进行加密处理
            if ("true".equalsIgnoreCase(jobModel.getAllProperties().get(
                    "zeus.secret.script"))) {
                if (!jobModel.getAdmin()
                        && !jobModel.getOwner()
                        .equals(LoginUser.getUser().getUid())) {
                    jobModel.setScript("脚本已加密,如需查看请联系相关负责人分配权限");
                }
            }
            if (jobBean.getJobDescriptor().getTimezone() == null
                    || "".equals(jobBean.getJobDescriptor().getTimezone())) {
                jobModel.setDefaultTZ(DateUtil.getDefaultTZStr());
            } else {
                jobModel.setDefaultTZ(jobBean.getJobDescriptor().getTimezone());
            }
            jobModel.setOffRaw(jobBean.getJobDescriptor().getOffRaw());
            jobModel.setJobCycle(jobBean.getJobDescriptor().getCycle());
            jobModel.setHost(jobBean.getJobDescriptor().getHost());
            jobModel.setHostGroupId(jobBean.getJobDescriptor().getHostGroupId());
            return this.buildResponse(jobModel);
        } catch (Exception e) {
            log.error("load my job upstream data failed.", e);
            return this.buildResponse(ReturnCode.SYSTEM_ERROR, null);
        }
    }

    public List<String> getImportantContactUid(String jobId) {
        List<ZeusFollow> jobFollowers = followManager.findJobFollowers(jobId);
        List<ZUser> contactList = getContactList(jobFollowers, true);
        List<String> uidList = new ArrayList<String>();
        for (ZUser u : contactList) {
            uidList.add(u.getUid());
        }
        return uidList;
    }

    public List<ZUser> getContactList(List<ZeusFollow> jobFollowers, boolean isImportant) {

        List<String> uids = new ArrayList<String>();
        List<ZUser> results = new ArrayList<ZUser>();
        for (ZeusFollow zf : jobFollowers) {
            if (zf.isImportant() == isImportant) {
                uids.add(zf.getUid());
            }
        }
        List<ZeusUser> users = userManager.findListByUid(uids);
        for (ZeusUser user : users) {
            ZUser tmp = new ZUser();
            tmp.setName(user.getName());
            tmp.setUid(user.getUid());
            results.add(tmp);
        }
        return results;
    }

    public List<ZUserContactTuple> getAllContactList(String jobId) {
        List<ZeusFollow> jobFollowers = followManager.findJobFollowers(jobId);
        List<ZUser> importantContactList = getImportantContactList(jobFollowers);
        List<ZUser> notImportantContactList = getNotImportantContactList(jobFollowers);
        List<ZUserContactTuple> results = new ArrayList<ZUserContactTuple>();
        for (ZUser u : importantContactList) {
            ZUserContactTuple tmp = new ZUserContactTuple(u, true);
            results.add(tmp);
        }
        for (ZUser u : notImportantContactList) {
            ZUserContactTuple tmp = new ZUserContactTuple(u, false);
            results.add(tmp);
        }
        return results;
    }

    public List<ZUser> getImportantContactList(List<ZeusFollow> jobFollowers) {
        return getContactList(jobFollowers, true);
    }

    public List<ZUser> getNotImportantContactList(List<ZeusFollow> jobFollowers) {
        return getContactList(jobFollowers, false);
    }

    @RequestMapping(value = "/get_host_group_name_by_id", method = RequestMethod.GET)
    public String getHostGroupNameById(@RequestParam(value = "id") String id) {
        String result = null;
        if (id != null) {
            ZeusHostGroup persist = hostGroupManager.getHostGroupName(id);
            result = persist.getName();
        }
        return result;
    }
}