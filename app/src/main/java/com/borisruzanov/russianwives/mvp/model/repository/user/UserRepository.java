package com.borisruzanov.russianwives.mvp.model.repository.user;

import android.support.annotation.NonNull;
import android.util.Log;

import com.borisruzanov.russianwives.Refactor.FirebaseRequestManager;
import com.borisruzanov.russianwives.models.ActionItem;
import com.borisruzanov.russianwives.models.ActionModel;
import com.borisruzanov.russianwives.models.FsUser;
import com.borisruzanov.russianwives.mvp.model.data.prefs.Prefs;
import com.borisruzanov.russianwives.utils.ActionCallback;
import com.borisruzanov.russianwives.utils.ActionCountCallback;
import com.borisruzanov.russianwives.utils.ActionItemCallback;
import com.borisruzanov.russianwives.utils.ActionsCountInfoCallback;
import com.borisruzanov.russianwives.utils.BoolCallback;
import com.borisruzanov.russianwives.utils.Consts;
import com.borisruzanov.russianwives.utils.NecessaryInfoCallback;
import com.borisruzanov.russianwives.utils.StringsCallback;
import com.borisruzanov.russianwives.utils.UserCallback;
import com.borisruzanov.russianwives.utils.ValueCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.borisruzanov.russianwives.utils.FirebaseUtils.getDeviceToken;
import static com.borisruzanov.russianwives.utils.FirebaseUtils.getNeededUsers;
import static com.borisruzanov.russianwives.utils.FirebaseUtils.getUid;

public class UserRepository {

    private FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private CollectionReference users = FirebaseFirestore.getInstance().collection(Consts.USERS_DB);
    private DatabaseReference realtimeReference = FirebaseDatabase.getInstance().getReference();

    private Prefs prefs;

    public UserRepository(Prefs prefs) {
        this.prefs = prefs;
    }

    /**
     * Saving FsUser to data base
     */
    //TODO поменять название баз данных на RtUsers / FsUsers
    public void saveUser() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        users.get().addOnCompleteListener(task -> {
            List<String> uidList = new ArrayList<>();

            for (DocumentSnapshot snapshot : task.getResult().getDocuments()) {
                uidList.add(snapshot.getString(Consts.UID));
            }

            if (!uidList.contains(getUid())) {
                createNewUser(currentUser.getDisplayName(), getDeviceToken(), getUid());
            } else {
                updateToken();
            }

        });
    }

    private boolean isOneDayGone() {
        float days = 0f;
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            if (!prefs.getDialogOpenDate().equals("+")) {
                Date date1 = dateFormat.parse(prefs.getDialogOpenDate());
                Date date2 = Calendar.getInstance().getTime();
                long diff = date2.getTime() - date1.getTime();
                days = (diff / (1000*60*60*24));
                Log.d("TimerDebug", "Num of days is" + String.valueOf(days) + " date is " + prefs.getDialogOpenDate());
            }
        } catch (ParseException e) {
            Log.d("TimerDebug", "In catch");
            e.printStackTrace();
        }
        Log.d("TimerDebug", "Prefs value is " + String.valueOf(prefs.getDialogOpenDate().equals("")));
        Log.d("TimerDebug", "Prefs value is " + String.valueOf(days >= 1));
        Log.d("TimerDebug", "Value of exp is" + String.valueOf(prefs.getDialogOpenDate().equals("") || days >= 1));
        return prefs.getDialogOpenDate().equals("") || days >= 1;
    }

    public void updateToken() {
        Map<String, Object> ntMap = new HashMap<>();
        ntMap.put(Consts.DEVICE_TOKEN, getDeviceToken());
        users.document(getUid()).update(ntMap);
        realtimeReference.child(Consts.USERS_DB).child(getUid()).updateChildren(ntMap);
    }

    private void createNewUser(String name, String token, String uid) {
        users.document(uid).set(FirebaseRequestManager.createNewUser(name, token, uid));

        Map<String, Object> niMap = new HashMap<>();
        niMap.put("created", "registered");
        niMap.put("device_token", token);
        niMap.put(Consts.IMAGE, "default");
        niMap.put(Consts.NAME, name);
        niMap.put(Consts.RATING, 0);
        niMap.put(Consts.ACHIEVEMENTS, new ArrayList<String>());
        niMap.put("online", ServerValue.TIMESTAMP);
        niMap.put(Consts.UID, uid);
        realtimeReference.child(Consts.USERS_DB).child(uid).setValue(niMap);
    }

    public void getTokens(ValueCallback callback) {
        users.document(getUid()).get().addOnCompleteListener(task -> {
            String fsToken = task.getResult().getString("device_token");
            callback.setValue("Firestore token is " + fsToken + " \n current token is " + getDeviceToken());
        });
    }

    public void setFirstOpenDate(){
        if (prefs.getFirstOpenDate().equals(Consts.DEFAULT)) {
            prefs.setFirstOpenDate();
        }
    }

    public void getNecessaryInfo(NecessaryInfoCallback callback) {
        users.document(getUid()).get().addOnCompleteListener(task -> {
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot.exists()) {
                String gender = snapshot.getString(Consts.GENDER);
                if (!gender.equals(Consts.DEFAULT)) {
                    String age = snapshot.getString(Consts.AGE);
                    if ((gender.equals(Consts.DEFAULT) && age.equals(Consts.DEFAULT))) {
                        callback.setInfo(gender, age);
                    } else if (gender.equals(Consts.DEFAULT) || age.equals(Consts.DEFAULT)) {
                        callback.setInfo(gender, age);
                    }
                }
            }
        });
    }

    public void setNecessaryInfo(String gender, String age) {
        Map<String, Object> necessaryInfoMap = new HashMap<>();
        if (!gender.equals(Consts.DEFAULT)) necessaryInfoMap.put(Consts.GENDER, gender);
        if (!age.equals(Consts.DEFAULT)) necessaryInfoMap.put(Consts.AGE, age);
        if (!necessaryInfoMap.isEmpty()) users.document(getUid()).update(necessaryInfoMap);
    }

    public boolean isGenderDefault() {
        return prefs.getGenderSearch().equals(Consts.DEFAULT);
    }

    public void setGender(String gender) {
        Map<String, Object> genderMap = new HashMap<>();
        if (!gender.equals(Consts.DEFAULT)) {
            genderMap.put(Consts.GENDER, gender);
            prefs.setGenderSearch(gender);
            //if (isUserExist()) users.document(getUid()).update(genderMap);
        }
    }

    public void hasNecessaryInfo(BoolCallback callback) {
        users.document(getUid()).get().addOnCompleteListener(task -> {
            if (task.getResult().exists()) {
                DocumentSnapshot snapshot = task.getResult();
                callback.setBool(!snapshot.getString(Consts.GENDER).equals(Consts.DEFAULT) &&
                        !snapshot.getString(Consts.AGE).equals(Consts.DEFAULT));
            }
        });
    }

    public void hasAdditionalInfo(StringsCallback callback) {
        users.document(getUid()).get().addOnCompleteListener(task -> {
            DocumentSnapshot snapshot = task.getResult();
            if (!snapshot.getString(Consts.GENDER).equals(Consts.DEFAULT) && !snapshot.getString(Consts.AGE).equals(Consts.DEFAULT)) {
                callback.setStrings(getListOfDefaults(snapshot));
            }
        });
    }

    public boolean isUserExist() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        return firebaseUser != null;
    }

    public void getAllCurrentUserInfo(final UserCallback callback) {
        users.document(getUid()).get()
                .addOnCompleteListener(task -> {
                    DocumentSnapshot snapshot = task.getResult();
                    if (snapshot.exists()) {
                        callback.setUser(snapshot.toObject(FsUser.class));
                    }
                });
    }

    public void getTransformedActions(ActionItemCallback callback) {
        List<ActionItem> actionItems = new ArrayList<>();
        getMergedActions(actionModels -> {
            List<String> uidList = new ArrayList<>();
            for (ActionModel model : actionModels) {
                uidList.add(model.getUid());
            }
            getNeededUsers(users, uidList, fsUserList -> {
                for (int i = 0; i < fsUserList.size(); i++) {
                    actionItems.add(new ActionItem(actionModels.get(i).getUid(), fsUserList.get(i).getName(),
                            fsUserList.get(i).getImage(), actionModels.get(i).getType(),
                            actionModels.get(i).getTimestamp()));
                }
                callback.setActionItems(actionItems);
            });
        });
    }

    public void getActionsCountInfo(ActionsCountInfoCallback callback) {
        getActionCount("Visits", visitsCount ->
                getActionCount("Likes", likesCount -> callback.setActions(visitsCount, likesCount)));
    }

    private void getLikes(ActionCallback callback) {
        realtimeReference.child("Likes").child(getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<ActionModel> actionModels = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    actionModels.add(new ActionModel(
                            Long.valueOf(snapshot.child("timestamp").getValue().toString()),
                            snapshot.getKey(),
                            "like"));
                }
                callback.setActionList(actionModels);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("ActionList", "getLikes" + databaseError.getMessage());
            }
        });
    }

    public void addUid() {
        realtimeReference.child("Users").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot: dataSnapshot.getChildren()) {
                    String uid = snapshot.getKey();
                    Log.d("RDUpdate", "Uid is " + uid);
                    Map<String, Object> uidMap = new HashMap<>();
                    uidMap.put(Consts.UID, uid);
                    snapshot.getRef().updateChildren(uidMap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d("RDUpdate", "Error message is " + databaseError.getMessage());
            }
        });
        /*users.get().addOnCompleteListener(task -> {
            for (DocumentSnapshot snapshot : task.getResult().getDocuments()) {
                if (snapshot.getString(Consts.UID) == null) {
                    Map<String, Object> uidMap = new HashMap<>();
                    uidMap.put(Consts.UID, snapshot.getId());
                    snapshot.getReference().update(uidMap);
                }
            }
        });*/
    }

    public void addInfo() {
        users.get().addOnCompleteListener(task -> {
            for (DocumentSnapshot snapshot : task.getResult().getDocuments()) {
                Map<String, Object> niMap = new HashMap<>();
                niMap.put("created", "registered");
                niMap.put("device_token", snapshot.getString("device_token"));
                niMap.put(Consts.IMAGE, snapshot.getString(Consts.IMAGE));
                niMap.put(Consts.NAME, snapshot.getString(Consts.NAME));
                niMap.put("online", ServerValue.TIMESTAMP);
                realtimeReference.child("Users").child(snapshot.getId()).setValue(niMap);
            }
        });
    }

    private void getVisits(ActionCallback callback) {
        realtimeReference.child("Visits").child(getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<ActionModel> actionModels = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Long timestamp = Long.valueOf(snapshot.child("timestamp").getValue().toString());
                    String fromUid = snapshot.child("fromUid").getValue().toString();
                    actionModels.add(new ActionModel(timestamp, fromUid, "visit"));
                }
                callback.setActionList(actionModels);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d("ActionList", "getVisits" + databaseError.getMessage());
            }
        });
    }

    private void getMergedActions(ActionCallback callback) {
        List<ActionModel> actionModels = new ArrayList<>();
        getLikes(actionLikeModels -> getVisits(actionVisitModels -> {
            actionModels.addAll(actionLikeModels);
            actionModels.addAll(actionVisitModels);
            Collections.sort(actionModels, (e1, e2) -> Long.compare(e2.getTimestamp(), e1.getTimestamp()));
            callback.setActionList(actionModels);
        }));
    }

    private void getActionCount(String dbName, ActionCountCallback callback) {
        realtimeReference.child(dbName).child(getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                callback.setActionCount(dataSnapshot.getChildrenCount());
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }

    private List<String> getListOfDefaults(DocumentSnapshot document) {
        List<String> valuesList = new ArrayList<>();
        for (String field : Consts.fieldKeyList) {
            String value = document.getString(field);
            if (value.toLowerCase().trim().equals(Consts.DEFAULT)) {
                valuesList.add(field);
            }
        }
        return valuesList;
    }

}
