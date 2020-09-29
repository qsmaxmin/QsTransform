package com.qsmaxmin.plugin.extension;

import java.util.List;

/**
 * @CreateBy qsmaxmin
 * @Date 2020/9/28 18:13
 * @Description
 */
public class ModelQsBaseInfo {

    /**
     * url : https://api.github.com/repos/qsmaxmin/QsBase/releases/31874819
     * assets_url : https://api.github.com/repos/qsmaxmin/QsBase/releases/31874819/assets
     * upload_url : https://uploads.github.com/repos/qsmaxmin/QsBase/releases/31874819/assets{?name,label}
     * html_url : https://github.com/qsmaxmin/QsBase/releases/tag/10.9.8
     * id : 31874819
     * node_id : MDc6UmVsZWFzZTMxODc0ODE5
     * tag_name : 10.9.8
     * target_commitish : master
     * name :
     * draft : false
     * author : {"login":"qsmaxmin","id":10693530,"node_id":"MDQ6VXNlcjEwNjkzNTMw","avatar_url":"https://avatars1.githubusercontent.com/u/10693530?v=4","gravatar_id":"","url":"https://api.github.com/users/qsmaxmin","html_url":"https://github.com/qsmaxmin","followers_url":"https://api.github.com/users/qsmaxmin/followers","following_url":"https://api.github.com/users/qsmaxmin/following{/other_user}","gists_url":"https://api.github.com/users/qsmaxmin/gists{/gist_id}","starred_url":"https://api.github.com/users/qsmaxmin/starred{/owner}{/repo}","subscriptions_url":"https://api.github.com/users/qsmaxmin/subscriptions","organizations_url":"https://api.github.com/users/qsmaxmin/orgs","repos_url":"https://api.github.com/users/qsmaxmin/repos","events_url":"https://api.github.com/users/qsmaxmin/events{/privacy}","received_events_url":"https://api.github.com/users/qsmaxmin/received_events","type":"User","site_admin":false}
     * prerelease : false
     * created_at : 2020-09-28T08:38:43Z
     * published_at : 2020-09-28T08:40:12Z
     * assets : []
     * tarball_url : https://api.github.com/repos/qsmaxmin/QsBase/tarball/10.9.8
     * zipball_url : https://api.github.com/repos/qsmaxmin/QsBase/zipball/10.9.8
     * body :
     */
    public String      url;
    public String      assets_url;
    public String      upload_url;
    public String      html_url;
    public int         id;
    public String      node_id;
    public String      tag_name;
    public String      target_commitish;
    public String      name;
    public boolean     draft;
    public AuthorModel author;
    public boolean     prerelease;
    public String      created_at;
    public String      published_at;
    public String      tarball_url;
    public String      zipball_url;
    public String      body;
    public List<?>     assets;

    public static class AuthorModel {
        /**
         * login : qsmaxmin
         * id : 10693530
         * node_id : MDQ6VXNlcjEwNjkzNTMw
         * avatar_url : https://avatars1.githubusercontent.com/u/10693530?v=4
         * gravatar_id :
         * url : https://api.github.com/users/qsmaxmin
         * html_url : https://github.com/qsmaxmin
         * followers_url : https://api.github.com/users/qsmaxmin/followers
         * following_url : https://api.github.com/users/qsmaxmin/following{/other_user}
         * gists_url : https://api.github.com/users/qsmaxmin/gists{/gist_id}
         * starred_url : https://api.github.com/users/qsmaxmin/starred{/owner}{/repo}
         * subscriptions_url : https://api.github.com/users/qsmaxmin/subscriptions
         * organizations_url : https://api.github.com/users/qsmaxmin/orgs
         * repos_url : https://api.github.com/users/qsmaxmin/repos
         * events_url : https://api.github.com/users/qsmaxmin/events{/privacy}
         * received_events_url : https://api.github.com/users/qsmaxmin/received_events
         * type : User
         * site_admin : false
         */

        public String  login;
        public int     id;
        public String  node_id;
        public String  avatar_url;
        public String  gravatar_id;
        public String  url;
        public String  html_url;
        public String  followers_url;
        public String  following_url;
        public String  gists_url;
        public String  starred_url;
        public String  subscriptions_url;
        public String  organizations_url;
        public String  repos_url;
        public String  events_url;
        public String  received_events_url;
        public String  type;
        public boolean site_admin;
    }
}
