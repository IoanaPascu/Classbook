package com.ioanapascu.edfocus.shared;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.ioanapascu.edfocus.BaseActivity;
import com.ioanapascu.edfocus.R;
import com.ioanapascu.edfocus.model.Contact;
import com.ioanapascu.edfocus.model.RequestInfo;
import com.ioanapascu.edfocus.model.UserAccountSettings;
import com.ioanapascu.edfocus.others.ContactsListAdapter;
import com.ioanapascu.edfocus.utils.FirebaseUtils;

import java.util.ArrayList;
import java.util.Map;

public class ContactsFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "ContactsFragment";

    // widgets
    private RecyclerView mContactsRecyclerView;
    private FloatingActionButton mFabAddContact;
    private EditText mSearchEditText;

    // variables
    private ArrayList<Contact> mContacts;
    private ArrayList<RequestInfo> mRequests;
    private ContactsListAdapter mContactsListAdapter;
    private FirebaseUtils firebase;

    public ContactsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firebase = new FirebaseUtils(getContext());
    }

    /**
     * Display contacts for the current user
     */
    private void displayContacts() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        firebase.mContactsRef.child(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mContacts.clear();
                mContactsListAdapter.notifyDataSetChanged();

                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    String contactId = data.getValue(String.class);

                    // for this contact (user) id get info to display in the contacts list
                    showContactData(contactId);
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    /**
     * Get info to display in the contacts list for the contact with given id.
     *
     * @param id
     */
    private void showContactData(final String id) {
        firebase.mUserAccountSettingsRef.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                UserAccountSettings settings = dataSnapshot.getValue(UserAccountSettings.class);

                Contact contact = new Contact();
                contact.setId(id);
                contact.setName(settings.getFirstName() + " " + settings.getLastName());
                contact.setEmail(settings.getEmail());
                contact.setProfilePhoto(settings.getProfilePhoto());
                contact.setUserType(settings.getUserType());

                mContacts.add(contact);
                mContactsListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    /**
     * Display requests for the current user
     */
    public void displayRequests() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        firebase.mRequestsRef.child(currentUser.getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mRequests.clear();

                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    Map<String, Object> newRequest = (Map<String, Object>) data.getValue();

                    String id = data.getKey();
                    String requestType = (String) newRequest.get("requestType");

                    // for this contact (user) id get info to display in the requests list
                    showRequestData(id, requestType);
                }

                mContactsListAdapter.notifyDataSetChanged();

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    /**
     * Get info to display in the requests list for the contact with given id.
     *
     * @param id
     */
    private void showRequestData(final String id, final String requestType) {
        firebase.mUserAccountSettingsRef.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                UserAccountSettings settings = dataSnapshot.getValue(UserAccountSettings.class);
                Log.d(TAG, "showRequestData : " + settings.toString());

                RequestInfo request = new RequestInfo();
                request.setPersonId(id);
                request.setName(settings.getDisplayName());
                request.setProfilePhoto(settings.getProfilePhoto());
                request.setRequestType(requestType);

                mRequests.add(request);
                mContactsListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BaseActivity.hideKeyboard(getActivity());

        mContacts = new ArrayList<>();
        mRequests = new ArrayList<>();

        mFabAddContact = view.findViewById(R.id.fab_add_contact);
        mContactsRecyclerView = view.findViewById(R.id.recycler_view_contacts);
        mSearchEditText = view.findViewById(R.id.edit_text_search_contact);

        mContactsListAdapter = new ContactsListAdapter(getContext(), mContacts, mRequests);
        mContactsRecyclerView.setAdapter(mContactsListAdapter);
        mContactsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        mFabAddContact.setOnClickListener(this);

        displayContacts();
        displayRequests();

        // filter contacts according to text that user enters
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // filter list according to user input
                filterContactsList(s.toString());
            }
        });
    }

    void filterContactsList(String text){
        ArrayList<Contact> temp = new ArrayList<>();
        text = text.toLowerCase();

        for(Contact contact: mContacts){
            if(contact.getName().toLowerCase().contains(text) || contact.getEmail().contains(text)){
                temp.add(contact);
            }
        }

        //update recycler view
        mContactsListAdapter.updateLists(temp);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onClick(View view) {
        if (view == mFabAddContact) {
            // jump to search activity
            startActivity(new Intent(getActivity(), SearchActivity.class));
        }
    }

}
