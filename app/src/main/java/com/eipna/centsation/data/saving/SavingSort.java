package com.eipna.centsation.data.saving;


public enum SavingSort {

    NAME("sort_name"),
    VALUE("sort_value"),
    GOAL("sort_goal"),
    DEADLINE("sort_deadline"),
    ASCENDING("sort_ascending"),
    DESCENDING("sort_descending");

    public final String SORT;

    SavingSort(String sort) {
        this.SORT = sort;
    }
}