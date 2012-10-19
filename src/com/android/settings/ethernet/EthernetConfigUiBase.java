package com.android.settings.ethernet;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Button;

public interface EthernetConfigUiBase {
    public Context getContext();
    public EthernetConfigController getController();
    public LayoutInflater getLayoutInflater();

    public void setTitle(int id);
    public void setTitle(CharSequence title);

    public void setSubmitButton(CharSequence text);
    public void setCancelButton(CharSequence text);
    public Button getSubmitButton();
    public Button getCancelButton();
}
