package com.softdesign.devintensive.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.softdesign.devintensive.R;
import com.softdesign.devintensive.data.managers.DataManager;
import com.softdesign.devintensive.data.network.req.UserLoginReq;
import com.softdesign.devintensive.data.network.res.UserListRes;
import com.softdesign.devintensive.data.network.res.UserModelRes;
import com.softdesign.devintensive.data.storage.models.Repository;
import com.softdesign.devintensive.data.storage.models.RepositoryDao;
import com.softdesign.devintensive.data.storage.models.User;
import com.softdesign.devintensive.data.storage.models.UserDao;
import com.softdesign.devintensive.utils.AppConfig;
import com.softdesign.devintensive.utils.ConstantManager;
import com.softdesign.devintensive.utils.NetworkStatusChecker;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by root on 07.07.2016.
 */
public class AuthActivity extends BaseActivity implements View.OnClickListener {

    private Button mSignIn;
    private TextView mRememberPassword;
    private EditText mLogin,mPassword;
    private CoordinatorLayout mCoordinatorLayout;
    private DataManager mDataManager;
    private RepositoryDao mRepositoryDao;
    private UserDao mUserDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mDataManager = DataManager.getInstance();

        mUserDao = mDataManager.getDaoSession().getUserDao();
        mRepositoryDao = mDataManager.getDaoSession().getRepositoryDao();

        mCoordinatorLayout = (CoordinatorLayout) findViewById(R.id.main_coordinator_container);
        mSignIn = (Button) findViewById(R.id.login_btn);
        mRememberPassword = (TextView) findViewById(R.id.remember_txt);
        mLogin = (EditText) findViewById(R.id.login_email_et);
        mPassword = (EditText) findViewById(R.id.login_password_et);

        mRememberPassword.setOnClickListener(this);
        mSignIn.setOnClickListener(this);


    }

    @Override
    public void onClick(View v) {

        switch (v.getId()){

            case R.id.login_btn:
                signIn();
                break;
            case R.id.remember_txt:
                rememberPassword();
                break;
        }

    }

    private void showSnackBar(String message){
        Snackbar.make(mCoordinatorLayout,message,Snackbar.LENGTH_LONG).show();
    }

    private void rememberPassword(){
        Intent rememberIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://devintensive.softdesign-apps.ru/forgotpass"));
        startActivity(rememberIntent);

    }

    private void loginSuccess(UserModelRes userModel){

        showProgress();
        userModel.getData().getToken();
        mDataManager.getPreferencesManager().saveAuthToken(userModel.getData().getToken());
        mDataManager.getPreferencesManager().saveUserId(userModel.getData().getUser().getId());
        saveUserValues(userModel);
        saveUserProfileValues(userModel);
        saveUserPhotoAndAvatar(userModel);
        saveUserInDb();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent loginIntent = new Intent(AuthActivity.this, UserListActivity.class);
                startActivity(loginIntent);
            }
        }, AppConfig.START_DELAY);


    }

    private void signIn() {

        if (NetworkStatusChecker.isNetworkAvailiable(this)) {

            Call<UserModelRes> call = mDataManager.loginUser(new UserLoginReq(mLogin.getText().toString(), mPassword.getText().toString()));
            call.enqueue(new Callback<UserModelRes>() {
                @Override
                public void onResponse(Call<UserModelRes> call, Response<UserModelRes> response) {
                    if (response.code() == 200) {
                        loginSuccess(response.body());
                    } else if (response.code() == 404) {
                        showSnackBar("Неверный логин и пароль");
                    } else {
                        showSnackBar("Что то не так");
                    }
                }

                @Override
                public void onFailure(Call<UserModelRes> call, Throwable t) {
                    //TODO: обработать ошибки ретрофита
                }
            });
        }
        else{
            showSnackBar("Сеть не доступна на данный момент, попробуйте позже");
        }
    }

    private void saveUserValues(UserModelRes userModel){
        int[] userValues = {
                userModel.getData().getUser().getProfileValues().getRating(),
                userModel.getData().getUser().getProfileValues().getLinesCode(),
                userModel.getData().getUser().getProfileValues().getProjects(),
        };

        mDataManager.getPreferencesManager().saveUserProfileValues(userValues);
    }
    private void saveUserProfileValues(UserModelRes userModel){
        ArrayList<String> userProfileValues = new ArrayList<>();
                userProfileValues.add(userModel.getData().getUser().getContacts().getPhone());
                userProfileValues.add(userModel.getData().getUser().getContacts().getEmail());
                userProfileValues.add(userModel.getData().getUser().getContacts().getVk());
                userProfileValues.add(userModel.getData().getUser().getRepositories().getRepo().get(ConstantManager.NULL).getGit());
                userProfileValues.add(userModel.getData().getUser().getPublicInfo().getBio());

        mDataManager.getPreferencesManager().saveUserProfileData(userProfileValues);
    }
    private void saveUserPhotoAndAvatar(UserModelRes userModelRes){
        Uri photo = Uri.parse("http://devintensive.softdesign-apps.ru/storage/user/57683cd01d37a01c00ee0753/photo.jpg");
        Uri avatar = Uri.parse("http://devintensive.softdesign-apps.ru/storage/user/57683cd01d37a01c00ee0753/avatar.jpg");
        mDataManager.getPreferencesManager().saveUserPhoto(photo);
        mDataManager.getPreferencesManager().saveAvatarImg(avatar);
    }

    private void saveNameAndSurname(UserModelRes userModelRes){
        String firstName = userModelRes.getData().getUser().getFirstName();
        String surname = userModelRes.getData().getUser().getSecondName();
        mDataManager.getPreferencesManager().saveFirstAndSecondName(firstName,surname);
    }

    private void saveUserInDb(){
        Call<UserListRes> call = mDataManager.getUserListFromNetwork();

        call.enqueue(new Callback<UserListRes>() {
            @Override
            public void onResponse(Call<UserListRes> call, Response<UserListRes> response) {
                try {
                    if (response.code() == 200) {

                        List<Repository> allRepositories = new ArrayList<Repository>();
                        List<User> allUsers = new ArrayList<User>();

                        for (UserListRes.UserData userRes: response.body().getData()){

                            allRepositories.addAll(getRepoListfromUserRes(userRes));
                            allUsers.add(new User(userRes));

                        }
                            mRepositoryDao.insertOrReplaceInTx(allRepositories);
                            mUserDao.insertOrReplaceInTx(allUsers);

                    } else {
                        showSnackBar("Список пользователей не может быть получен");
                        Log.e(TAG, "onResponse: " + String.valueOf(response.errorBody().source()));

                    }
                }catch (NullPointerException e) {
                    e.printStackTrace();
                    showSnackBar("Что то пошло не так");
                }
            }

            @Override
            public void onFailure(Call<UserListRes> call, Throwable t) {
                showSnackBar(t.getMessage());
            }
        });
    }
    private List<Repository> getRepoListfromUserRes(UserListRes.UserData userData){
        final String userId = userData.getId();

        List<Repository> repositories = new ArrayList<>();
        for(UserModelRes.Repo repositoryRes: userData.getRepositories().getRepo()){
            repositories.add(new Repository(repositoryRes, userId));
        }

        return repositories;
    }
}
