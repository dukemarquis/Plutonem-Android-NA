package com.plutonem;

import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.view.MenuItem;

public class MainActivity extends AppCompatActivity implements Plutonem_hpage_card_fragment.OnFragmentInteractionListener {

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {

            return true;
        }
    };

    /**
     * The number of pages to show.
     */
    private static final int NUM_PAGES = 1;

    /**
     * The pager widget, which handles animation and allows swiping horizontally to access previous
     * and next wizard steps.
     */
    private ViewPager mPager;

    /**
     * The pager adapter, which provides the pages to the view pager widget.
     */
    private PagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_page_fragment);

        BottomNavigationView navView = findViewById(R.id.bottom_navigation_view);
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mPager = findViewById(R.id.plutonem_hpage_viewpager);
        pagerAdapter = new PlutonemHpagePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(pagerAdapter);

        TabLayout tabLayout = findViewById(R.id.plutonem_hpage_tablayout);
        tabLayout.setupWithViewPager(mPager);
    }

//    @Override
//    public void onNoteClick(int position) {
//
//        Intent intent = new Intent(this, Plutonem_cdetail.class);
//        startActivity(intent);
//    }

    /**
     * A simple pager adapter that represents 3 fragment_hpage_dashboard objects, in
     * sequence.
     */
    private class PlutonemHpagePagerAdapter extends FragmentStatePagerAdapter {

        public PlutonemHpagePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            return Plutonem_hpage_card_fragment.newInstance("", "");
        }

        @Override
        public int getCount() {
            return NUM_PAGES;
        }

        @Override
        public CharSequence getPageTitle(int position) {

            if (position == 0) {
                return getString(R.string.everything);
            } else if (position == 1) {
                return getString(R.string.underwear);
            } else if (position == 2) {
                return getString(R.string.cosmetic);
            }

            return "OBJECT " + (position + 1);
        }
    }

    public void onFragmentInteraction(Uri uri) { }

//    public void Plutonem_Transfer_PlutonemCdetail(View view) {
//
//
//    }
}
