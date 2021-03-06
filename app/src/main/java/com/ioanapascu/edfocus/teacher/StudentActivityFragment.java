package com.ioanapascu.edfocus.teacher;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.ioanapascu.edfocus.R;
import com.ioanapascu.edfocus.model.Absence;
import com.ioanapascu.edfocus.model.AbsenceDb;
import com.ioanapascu.edfocus.model.Course;
import com.ioanapascu.edfocus.model.Grade;
import com.ioanapascu.edfocus.model.GradeDb;
import com.ioanapascu.edfocus.others.StudentAbsencesStickyAdapter;
import com.ioanapascu.edfocus.others.StudentGradesStickyAdapter;
import com.ioanapascu.edfocus.utils.FirebaseUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

/**
 * Created by ioana on 3/15/2018.
 */
public class StudentActivityFragment extends Fragment implements View.OnClickListener {

    public static final String ARG_PAGE = "ARG_PAGE";
    public static final String STUDENT_ID = "STUDENT_ID";
    public static final String CLASS_ID = "CLASS_ID";

    // widgets
    HashMap<String, Long> mHeaderIds;
    StudentGradesStickyAdapter mGradesAdapter;
    StudentAbsencesStickyAdapter mAbsencesAdapter;
    private RelativeLayout mNoGradesLayout;

    // variables
    private int mPageIndex; // can be 0 (Grades Page) or 1(Absences Page)
    private String mClassId, mStudentId;
    private ArrayList<Grade> mGrades;
    private ArrayList<Absence> mAbsences;
    private FirebaseUtils firebase;

    public static StudentActivityFragment newInstance(int page, String studentId, String classId) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);
        args.putString(STUDENT_ID, studentId);
        args.putString(CLASS_ID, classId);
        StudentActivityFragment fragment = new StudentActivityFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPageIndex = getArguments().getInt(ARG_PAGE);
        mClassId = getArguments().getString(CLASS_ID);
        mStudentId = getArguments().getString(STUDENT_ID);
        firebase = new FirebaseUtils(getContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_student_activity, container, false);

        // widgets
        mNoGradesLayout = view.findViewById(R.id.layout_no_grades);

        mGrades = new ArrayList<>();
        mAbsences = new ArrayList<>();
        mHeaderIds = new HashMap<>();

        String userType = firebase.getCurrentUserType();

        if (mPageIndex == 0) {
            // display grades
            StickyListHeadersListView stickyList = view.findViewById(R.id.list_grades);
            mGradesAdapter = new StudentGradesStickyAdapter(getContext(), R.layout.row_grade,
                    R.layout.row_header, mGrades, mHeaderIds, mClassId, mStudentId, userType);
            stickyList.setAdapter(mGradesAdapter);
            getGrades();
        } else {
            // display absences
            StickyListHeadersListView stickyList = view.findViewById(R.id.list_grades);
            mAbsencesAdapter = new StudentAbsencesStickyAdapter(getContext(), R.layout.row_absence,
                    R.layout.row_header, mAbsences, mHeaderIds, mClassId, mStudentId, userType);
            stickyList.setAdapter(mAbsencesAdapter);
            getAbsences();
        }

        return view;
    }

    // returns a list of grades
    private void getGrades() {
        // get all grades for this class and for this student
        firebase.mStudentGradesRef.child(mClassId).child(mStudentId).orderByChild("courseId").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mGrades.clear();

                if (dataSnapshot.getChildrenCount() > 0) {
                    mNoGradesLayout.setVisibility(View.GONE);

                    for (DataSnapshot data : dataSnapshot.getChildren()) {
                        final GradeDb gradeDb = data.getValue(GradeDb.class);

                        // retrieve course info
                        firebase.mClassCoursesRef.child(mClassId).child(gradeDb.getCourseId()).addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                Course course = dataSnapshot.getValue(Course.class);

                                mGrades.add(new Grade(gradeDb, course.getName()));
                                addToHashMap(course.getId());
                                mGradesAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                    }
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    // returns a list of grades
    private void getAbsences() {
        // retrieve absences for current class and current student
        firebase.mStudentAbsencesRef.child(mClassId).child(mStudentId).orderByChild("courseId").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                mAbsences.clear();

                if (dataSnapshot.getChildrenCount() > 0) {
                    mNoGradesLayout.setVisibility(View.GONE);

                    for (DataSnapshot data : dataSnapshot.getChildren()) {
                        final AbsenceDb absenceDb = data.getValue(AbsenceDb.class);

                        // retrieve course info
                        firebase.mClassCoursesRef.child(mClassId).child(absenceDb.getCourseId()).addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                Course course = dataSnapshot.getValue(Course.class);

                                mAbsences.add(new Absence(absenceDb, course.getName()));
                                addToHashMap(course.getId());
                                mAbsencesAdapter.notifyDataSetChanged();
                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });

                    }
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void addToHashMap(String courseId) {
        // if this course id already exists return
        if (mHeaderIds.containsKey(courseId)) {
            return;
        }

        // insert this courseId with value of bigget id + 1
        // get biggest id
        long maxId = -1;
        for (Map.Entry<String, Long> entry : mHeaderIds.entrySet()) {
            if (entry.getValue() > maxId) {
                maxId = entry.getValue();
            }
        }

        mHeaderIds.put(courseId, maxId + 1);

    }

    @Override
    public void onClick(View view) {

    }
}