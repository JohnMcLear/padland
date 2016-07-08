package com.mikifus.padland.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.mikifus.padland.Models.Server;
import com.mikifus.padland.Models.ServerModel;
import com.mikifus.padland.ServerListActivity;

/**
 * Created by mikifus on 29/05/16.
 */
public class ServerListAdapter extends ArrayAdapter {

    private ServerListActivity mContext;
    private int layout_resource;
    private ServerModel serverModel;

    public ServerListAdapter(ServerListActivity context, int resource) {
        super(context, resource);
        mContext = context;
        layout_resource = resource;
        serverModel = new ServerModel(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        Server server = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(layout_resource, parent, false);
        }

        server = serverModel.getServerAt(position);

//        server.name = "bbbb";
//        server.url_padprefix = "test@test";

        ((TextView) convertView.findViewById(android.R.id.text1)).setText(server.name);
        ((TextView) convertView.findViewById(android.R.id.text2)).setText(server.url);
        // Lookup view for data population
//        TextView tvName = (TextView) convertView.findViewById(R.id.tvName);
//        TextView tvHome = (TextView) convertView.findViewById(R.id.tvHome);
        // Populate the data into the template view using the data object
//        tvName.setText(user.name);
//        tvHome.setText(user.hometown);
        // Return the completed view to render on screen
        return convertView;
    }

    @Override
    public int getCount() {
        return serverModel.getServerCount();
    }

    @Override
    public Server getItem(int position) {
        return new Server();
    }
}
