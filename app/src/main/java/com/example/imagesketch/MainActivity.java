package com.example.imagesketch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.drm.ProcessedData;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sdsmdg.tastytoast.TastyToast;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int TAKE_PHOTO = 101;
    private static final int TAKE_ALBUM = 102;
    private static final int SUCCESS = 103;
    private static final int ERROR = 104;

    boolean isGenerated = false;
    boolean isGetPicture = false;

    private ImageView ivPicture;
    private ImageView ivProcessedPicture;
    private TextView tvReGenerate;
    private TextView tvConfirm;
    private TextView tvReChoose;
    private TextView tvSave;
    private LinearLayout llGenerate;

    private LinearLayout llGenerate1;
    private TextView tvSave1;
    private TextView tvRegenerate1;
    private ImageView ivProcessedPicture1;

    private Bitmap rawPicture;
    private Bitmap processedPicture;
    private Bitmap processedPicture1;
    private Uri rawImageUri;

    private ProgressDialog generateDialog;

    /*
   设置handler接收网络线程的信号并处理
    */
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SUCCESS:
                    generateDialog.dismiss();
                    isGenerated = true;
                    ivProcessedPicture.setImageBitmap(processedPicture);
                    ivProcessedPicture1.setImageBitmap(processedPicture1);
                    //ivProcessedPicture.setImageDrawable(getResources().getDrawable(R.drawable.img_add_background));
                    llGenerate.setVisibility(View.VISIBLE);
                    llGenerate1.setVisibility(View.VISIBLE);
                    TastyToast.makeText(getApplicationContext(), "生成成功！", Toast.LENGTH_SHORT, TastyToast.SUCCESS);
                    break;
                case ERROR:
                    generateDialog.dismiss();
                    TastyToast.makeText(getApplicationContext(), "生成失败,请换一张图片", Toast.LENGTH_SHORT, TastyToast.ERROR);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        // 获得SharedPreference的修改及取得
        final SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
        SharedPreferences preferences = getSharedPreferences("data", MODE_PRIVATE);

        // 弹出一个授予权限提示的对话框，增加人性度
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(MainActivity.this);
        alertBuilder.setTitle("使用提示");
        alertBuilder.setMessage("由于该应用要使用到拍摄和文件相关存储功能,请您授予这些权限。");
        alertBuilder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 获取权限部分
                AndPermission.with(MainActivity.this)
                        .runtime()
                        .permission(Permission.CAMERA, Permission.WRITE_EXTERNAL_STORAGE, Permission.READ_EXTERNAL_STORAGE)
                        .onGranted(new Action<List<String>>() {
                            @Override
                            public void onAction(List<String> data) {
                                editor.putBoolean("isGranted", true);
                                editor.apply();
                            }
                        })
                        .onDenied(new Action<List<String>>() {
                            @Override
                            public void onAction(List<String> data) {
                                TastyToast.makeText(getApplicationContext(), "您有权限没有授予，可能导致部分功能无法使用", Toast.LENGTH_SHORT, TastyToast.WARNING);
                                editor.putBoolean("isGranted", false);
                                editor.apply();
                            }
                        })
                        .start();
            }
        });
        boolean isGranted = preferences.getBoolean("isGranted", false);
        if (!isGranted){
            alertBuilder.show();
        }

        // 设置确定使用标签的触发
        tvConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isGenerated){
                    TastyToast.makeText(getApplicationContext(), "请点击‘新生成’", Toast.LENGTH_SHORT, TastyToast.WARNING);
                }else{
                    if (isGetPicture){
                        generateDialog.setCanceledOnTouchOutside(false);
                        generateDialog.setMessage("正在生成...");
                        generateDialog.show();
                        final Message message = new Message();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                if (transform()){
                                    message.what = SUCCESS;
                                    handler.sendMessage(message);
                                }else {
                                    message.what = ERROR;
                                    handler.sendMessage(message);
                                }
                            }
                        }).start();
                    } else {
                        TastyToast.makeText(getApplicationContext(), "请先添加一张照片", Toast.LENGTH_SHORT, TastyToast.WARNING);
                    }
                }

            }
        });

        // 设置重选标签的触发
        tvReChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isGenerated){
                    TastyToast.makeText(getApplicationContext(), "请点击‘新生成’", Toast.LENGTH_SHORT, TastyToast.WARNING);
                }else {
                    if (isGetPicture){
                        getPicture();
                    }else {
                        TastyToast.makeText(getApplicationContext(), "请先添加一张照片", Toast.LENGTH_SHORT, TastyToast.WARNING);
                    }
                }
            }
        });

        // 设置保存标签的触发
        tvSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(save(0)){
                    TastyToast.makeText(getApplicationContext(), "保存成功！文件已经保存到/storage/emulated/0", Toast.LENGTH_SHORT, TastyToast.SUCCESS);
                }else {
                    TastyToast.makeText(getApplicationContext(), "保存失败！", Toast.LENGTH_SHORT, TastyToast.ERROR);
                }
            }
        });

        // 设置保存标签的触发
        tvSave1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(save(1)){
                    TastyToast.makeText(getApplicationContext(), "保存成功！文件已经保存到/storage/emulated/0", Toast.LENGTH_SHORT, TastyToast.SUCCESS);
                }else {
                    TastyToast.makeText(getApplicationContext(), "保存失败！", Toast.LENGTH_SHORT, TastyToast.ERROR);
                }
            }
        });

        // 设置添加照片标签的触发
        ivPicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isGetPicture){
                    getPicture();
                }else {
                    TastyToast.makeText(getApplicationContext(), "请点击‘重选’", Toast.LENGTH_SHORT, TastyToast.WARNING);
                }
            }
        });

        // 设置重新生成标签的触发
        tvReGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isGetPicture = false;
                isGenerated = false;
                llGenerate.setVisibility(View.GONE);
                llGenerate1.setVisibility(View.GONE);
                ivPicture.setImageDrawable(getResources().getDrawable(R.drawable.img_add_background));
            }
        });
        tvRegenerate1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isGetPicture = false;
                isGenerated = false;
                llGenerate.setVisibility(View.GONE);
                llGenerate1.setVisibility(View.GONE);
                ivPicture.setImageDrawable(getResources().getDrawable(R.drawable.img_add_background));
            }
        });
    }

    private void initView(){
        ivPicture = findViewById(R.id.iv_picture);
        ivProcessedPicture = findViewById(R.id.iv_generate_picture);
        tvReGenerate = findViewById(R.id.tv_regenerate);
        tvConfirm = findViewById(R.id.tv_confirm_to_use);
        tvReChoose = findViewById(R.id.tv_rechoose);
        tvSave = findViewById(R.id.tv_save);
        llGenerate = findViewById(R.id.llgenerate);
        generateDialog = new ProgressDialog(MainActivity.this);

        llGenerate1 = findViewById(R.id.llgenerate1);
        tvSave1 = findViewById(R.id.tv_save1);
        tvRegenerate1 = findViewById(R.id.tv_regenerate1);
        ivProcessedPicture1 = findViewById(R.id.iv_generate_picture1);

        llGenerate.setVisibility(View.INVISIBLE); // 设置初始的
        llGenerate1.setVisibility(View.INVISIBLE);
    }

    /**
     * transform函数，就是将rawPicture进行处理，生成得到processedPicture;
     * 这里的rawPicture就是Bitmap类型的原图，processdPicture同样也是一个Bitmap，且已经初始化
     * 成功返回ture, 反之false
     */
    private boolean transform() {
        int height = rawPicture.getHeight();
        int width = rawPicture.getWidth();
        int[][][] arr = new int[width][height][3];

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                // System.out.printf("i:%d j:%d\n", i, j);
                int pixel = rawPicture.getPixel(i, j);
                arr[i][j][0] = Color.red(pixel);
                arr[i][j][1] = Color.green(pixel);
                arr[i][j][2] = Color.blue(pixel);
            }
        }
        processedPicture = rawPicture.copy(Bitmap.Config.ARGB_4444 , true);
        int[][] result = a1(arr);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int red = result[i][j];
                int green = result[i][j];
                int blue = result[i][j];
                processedPicture.setPixel(i, j, Color.rgb(red, green, blue));
            }
        }
        processedPicture1 = rawPicture.copy(Bitmap.Config.ARGB_4444 , true);
        result = a2(arr);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int red = result[i][j];
                int green = result[i][j];
                int blue = result[i][j];
                processedPicture1.setPixel(i, j, Color.rgb(red, green, blue));
            }
        }

        return true;
    }

    private static int[][] a1(int[][][] image) {
        int[][][] temp = image;
        int h = image.length, w = image[0].length;
        for (int row = 0; row < h - 1; row++) {
            for (int col = 0; col < w - 1; col++) {
                for (int dim = 0; dim < 3; dim++) {
                    int p1 = image[row][col][dim];
                    int p2 = image[row + 1][col][dim];
                    int p3 = image[row][col + 1][dim];
                    temp[row][col][dim] = 255 - 2 * (int) Math.sqrt(Math.pow(p1 - p2, 2) + Math.pow(p1 - p3, 2));
                }
            }
        }
        int[][] result = new int[h][w];
        double c1 = 0.299, c2 = 0.587, c3 = 0.114;

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                if (row == h - 1 || col == w - 1) {
                    result[row][col] = 255;
                } else {
                    result[row][col] = (int) (c1 * temp[row][col][0] + c2 * temp[row][col][1] +
                            c3 * temp[row][col][2]);
                }
                if (result[row][col] > 255) {
                    result[row][col] = 255;
                }
                if (result[row][col] < 0) {
                    result[row][col] = 0;
                }
            }
        }

        return result;
    }

    private static int[][] a2(int[][][] image) {
        double[][] gaussian = new double[][]{
                {0.036883446013326, 0.039164190939482, 0.039955360077524, 0.039164190939482, 0.036883446013326},
                {0.039164190939482, 0.041585969255423, 0.042426061560694, 0.041585969255423, 0.039164190939482},
                {0.039955360077524, 0.042426061560694, 0.043283124856278, 0.042426061560694, 0.039955360077524},
                {0.039164190939482, 0.041585969255423, 0.042426061560694, 0.041585969255423, 0.039164190939482},
                {0.036883446013326, 0.039164190939482, 0.039955360077524, 0.039164190939482, 0.036883446013326}
        };
        int width = image.length, height = image[0].length;

        int[][] gray = new int[width][height];
        double[][] blurry = new double[width][height];
        int[][] result = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                gray[x][y] = (int) (0.299 * image[x][y][0] + 0.587 * image[x][y][1] + 0.114 * image[x][y][2]);
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                blurry[x][y] = 0;
                for (int row = 0; row < 5; row++) {
                    for (int col = 0; col < 5; col++) {
                        blurry[x][y] += gaussian[row][col] *
                                gray[Math.max(0, Math.min(width -1 , x + row - 2))][Math.max(0, Math.min(height - 1, y + col - 2))];
                    }
                }
                blurry[x][y] = 255 - blurry[x][y];
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int a = gray[x][y];
                double b = blurry[x][y];
                result[x][y] = (int) Math.min(255, a + (double)(a * b) / (255 - b));
            }
        }

        return result;
    }

    // 获取照片函数
    private void getPicture(){
        // 弹出对话框，选择让用户是照相还是选取照片
        final String[] choices = new String[]{"拍照","从相册中选取"};
        AlertDialog.Builder tipBuilder = new AlertDialog.Builder(MainActivity.this);
        tipBuilder.setTitle("提示");
        tipBuilder.setItems(choices, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case 0:
                        //TastyToast.makeText(getApplicationContext(), "点击了照相", Toast.LENGTH_SHORT, TastyToast.WARNING);
                        getPictureFromPhoto();
                        break;
                    case 1:
                        //TastyToast.makeText(getApplicationContext(), "点击了从相册中选取", Toast.LENGTH_SHORT, TastyToast.WARNING);
                        getPictureFromAlbum();
                        break;
                }
            }
        });
        tipBuilder.show();
    }

    // 摄像获取照片
    private void getPictureFromPhoto(){
        File outputImage = new File(getExternalCacheDir(), "output_image.jpg");
        try{
            if (outputImage.exists()){
                outputImage.delete();
            }
            outputImage.createNewFile();
        }catch (IOException e){
            e.printStackTrace();
        }

        rawImageUri = FileProvider.getUriForFile(MainActivity.this,
                "com.example.imagesketch.fileprovider", outputImage);
        Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
        intent.putExtra(MediaStore.EXTRA_OUTPUT, rawImageUri);
        startActivityForResult(intent, TAKE_PHOTO);
    }

    // 从相册中获取照片
    private void getPictureFromAlbum(){
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        startActivityForResult(intent, TAKE_ALBUM);
    }

    // 保存处理后的照片
    private boolean save(int which){
        //获得系统的时间，单位为毫秒,转换为妙
        long totalMilliSeconds = System.currentTimeMillis();
        long totalSeconds = totalMilliSeconds / 1000;

        //求出现在的秒
        long currentSecond = totalSeconds % 60;

        //求出现在的分
        long totalMinutes = totalSeconds / 60;
        long currentMinute = totalMinutes % 60;

        //求出现在的小时
        long totalHour = totalMinutes / 60;
        long currentHour = (totalHour + 8) % 24;

        String nowTime = currentHour + ":" + currentMinute + ":" + currentSecond + " CST";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (which == 0){
            processedPicture.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        }else {
            processedPicture1.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        }


        File file = new File(Environment.getExternalStorageDirectory(), nowTime + ".jpg");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(baos.toByteArray());
                fos.flush();
                fos.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 得到结果的回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode){
            case TAKE_PHOTO:
                if (resultCode == RESULT_OK){
                    try {
                        rawPicture = getBitmapFormUri(rawImageUri);
                        ivPicture.setImageBitmap(rawPicture);
                        isGetPicture = true;
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
                break;
            case TAKE_ALBUM:
                if (resultCode == RESULT_OK){
                    try{
                        rawPicture = getBitmapFormUri(data.getData());
                        ivPicture.setImageBitmap(rawPicture);
                        isGetPicture = true;
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
                break;
        }
    }

    // 尺寸压缩函数
    public Bitmap getBitmapFormUri(Uri uri) throws IOException, IOException {

        InputStream input = getContentResolver().openInputStream(uri);

        //这一段代码是不加载文件到内存中也得到bitmap的真是宽高，主要是设置inJustDecodeBounds为true
        BitmapFactory.Options onlyBoundsOptions = new BitmapFactory.Options();

        onlyBoundsOptions.inJustDecodeBounds = true;//不加载到内存
//        onlyBoundsOptions.inDither = true;//optional
//        onlyBoundsOptions.inPreferredConfig = Bitmap.Config.RGB_565;//optional

        BitmapFactory.decodeStream(input, null, onlyBoundsOptions);

        input.close();

        int originalWidth = onlyBoundsOptions.outWidth;
        int originalHeight = onlyBoundsOptions.outHeight;

        if ((originalWidth == -1) || (originalHeight == -1))
            return null;

        //图片分辨率以480x800为标准
        float hh = 1280f;//这里设置高度为800f
        float ww = 720f;//这里设置宽度为480f
        //缩放比，由于是固定比例缩放，只用高或者宽其中一个数据进行计算即可
        int be = 1;//be=1表示不缩放
        if (originalWidth > originalHeight && originalWidth > ww) {//如果宽度大的话根据宽度固定大小缩放
            be = (int) (originalWidth / ww);
        } else if (originalWidth < originalHeight && originalHeight > hh) {//如果高度高的话根据宽度固定大小缩放
            be = (int) (originalHeight / hh);
        }
        if (be <= 0)
            be = 1;
        //比例压缩
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        bitmapOptions.inSampleSize = be;//设置缩放比例
//        bitmapOptions.inDither = true;
//        bitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        input = getContentResolver().openInputStream(uri);

        Bitmap bitmap = BitmapFactory.decodeStream(input, null, bitmapOptions);

        input.close();

        return bitmap;
    }
}


