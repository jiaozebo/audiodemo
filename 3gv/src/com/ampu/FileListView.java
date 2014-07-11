package com.ampu;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import util.CommonMethod;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * 文件管理器类
 * 
 * @author John
 * @version 1.0
 * @date 2011-12-10
 */
public class FileListView extends ListView
{

    private File root;

    public FileListView(Context context)
    {
        super(context);
    }

    public FileListView(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    public FileListView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public boolean initWithPath(String path, String[] filters)
    {
        File file = new File(path);
        if (file.exists())
        {
            initWithFile(file, filters);
            return true;
        }
        return false;
    }

    public void initWithFile(File file, String[] filters)
    {
        root = file;
        if (getAdapter() == null)
        {
            View view = LayoutInflater.from(getContext()).inflate(
                R.layout.file_root, null);
            TextView tv_name = (TextView) view.findViewById(R.id.tv_file_name);
            tv_name.setText(file.getName());
            ImageView iv_icon = (ImageView) view
                .findViewById(R.id.iv_file_icon);
            iv_icon.setImageResource(R.drawable.im_diretion);
            view.setTag(file);
            addHeaderView(view);
        }
        else
        {
            View view = getChildAt(0);
            TextView tView = (TextView) view.findViewById(R.id.tv_file_name);
            tView.setText(file.getName());
            ImageView iv_icon = (ImageView) view
                .findViewById(R.id.iv_file_icon);
            iv_icon.setImageResource(R.drawable.im_diretion);
            view.setTag(file);
        }
        FileAdapter adapter = new FileAdapter(file, filters);
        setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    public File getRoot()
    {
        return root;
    }

    public static boolean fileInFilters(File file, String[] filters)
    {
        String suffix = CommonMethod.getSuffix(file);
        for (int i = 0; i < filters.length; i++)
        {
            if (filters[i].equalsIgnoreCase(suffix))
            {
                return true;
            }
        }
        return false;
    }

    public class FileAdapter extends BaseAdapter
    {

        private File root;
        private List<File> files;

        public FileAdapter(File file, String[] filters)
        {
            super();
            this.root = file;
            File[] tempfiles = root.listFiles();
            files = new ArrayList<File>();
            for (int i = 0; i < tempfiles.length; i++)
            {
                File file2 = tempfiles[i];
                if (file2.isDirectory() || fileInFilters(file2, filters))
                {
                    files.add(file2);
                }
            }
            Collections.sort(files);
        }

        @Override
        public int getCount()
        {
            return files.size();
        }

        @Override
        public Object getItem(int arg0)
        {
            return files.get(arg0);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            if (convertView == null)
            {
                convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.file_item, null);
            }
            TextView tv_name = (TextView) convertView
                .findViewById(R.id.tv_file_name);
            File file = (File) getItem(position);
            tv_name.setText(file.getName());

            ImageView iv_icon = (ImageView) convertView
                .findViewById(R.id.iv_file_icon);

            int icon = 0;
            if (file.isDirectory())
            {
                icon = R.drawable.im_diretion;
            }
            else
            {
                String suffix = CommonMethod.getSuffix(file);
                if (suffix.equalsIgnoreCase("avi"))
                {
                    icon = R.drawable.im_record;
                }
                else if (suffix.equalsIgnoreCase("jpg")
                        || suffix.equalsIgnoreCase("png"))
                {
                    icon = R.drawable.im_picture;
                }
            }
            iv_icon.setImageResource(icon);
            convertView.setTag(file);
            return convertView;
        }

    }
}
