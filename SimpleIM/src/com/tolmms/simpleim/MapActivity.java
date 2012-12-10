package com.tolmms.simpleim;

import java.util.ArrayList;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.MinimapOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.tolmms.simpleim.datatypes.UserInfo;
import com.tolmms.simpleim.interfaces.IAppManager;
import com.tolmms.simpleim.services.IMService;
import com.tolmms.simpleim.storage.TemporaryStorage;

public class MapActivity extends Activity {
	public static final long SECONDS_TO_UPDATE_THE_MAP = 30 * 1000; //miliseconds
	private MapView mMapView;
	private MapController mMapController;
	private MinimapOverlay miniMapOverlay;
	
	ItemizedIconOverlay<OverlayItem> othersPositionOverlay;
	ItemizedIconOverlay<OverlayItem> myPositionOverlay;
	
	
	/***********************************/
	/* stuff for service */
	/***********************************/
	private IAppManager iMService;

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			// This is called when the connection with the service has been
            // unexpectedly disconnected - that is, its process crashed.
            // Because it is running in our same process, we should never see this happen
			
			iMService.viewingMap(false);
			
			
			iMService = null;
			
			if (MainActivity.DEBUG)
				Toast.makeText(MapActivity.this, "ERROR. service disconnected", Toast.LENGTH_SHORT).show();
			
			
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			// This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
			
			iMService = ((IMService.IMBinder) service).getService();
			
			if (MainActivity.DEBUG)
				Toast.makeText(MapActivity.this, "chiamato onServiceConnected", Toast.LENGTH_SHORT).show();
			
			
			
			//TODO mettere per da vero!
//			if (!iMService.isUserLoggedIn()) {
//				startActivity(new Intent(MapActivity.this, MainActivity.class));
//				LoggedUser.this.finish();
//			}
			
			//TODO da mettere una chiamata su imservice per avvisare che sto guardando la mappa
			iMService.viewingMap(true);
		}
		
	};
	/**********************************/
	
	
	private Handler handler;
	private Runnable runnable;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map);

		mMapView = (MapView) findViewById(R.id.mapview);
		mMapView.setTileSource(TileSourceFactory.MAPNIK);
		mMapView.setBuiltInZoomControls(true);
		
		mMapController = mMapView.getController();
		mMapController.setZoom(13);
		
		initializeMyPositionOverlay();
		initializeOtherPositionOverlay();
			
		 
		miniMapOverlay = new MinimapOverlay(this, this.mMapView.getTileRequestCompleteHandler());
		this.mMapView.getOverlays().add(miniMapOverlay);

		// Show the Up button in the action bar.
		// getActionBar().setDisplayHomeAsUpEnabled(true);
		
		
		LocalBroadcastManager.
		getInstance(this).
		registerReceiver(myPositionChangesMessageReceiver, 
				new IntentFilter(IAppManager.INTENT_ACTION_USER_POSITION_CHANGED));
		
		LocalBroadcastManager.
		getInstance(this).
		registerReceiver(otherPositionMessageReceiver, 
				new IntentFilter(IAppManager.INTENT_ACTION_OTHER_POSITION_CHANGED));
		
		
//		updateMyPositionOnTheMap();
//		updateOthersPositionOnTheMap();
		
		
		
		handler = new Handler();
		

		runnable = new Runnable() 
		{

		    public void run() 
		    {
		    	updateMyPositionOnTheMap();
				updateOthersPositionOnTheMap();
				
				if (MainActivity.DEBUG)
					Toast.makeText(MapActivity.this, "fatto update", Toast.LENGTH_SHORT).show();
				
				handler.postDelayed(this, SECONDS_TO_UPDATE_THE_MAP);
		    }
		};
		
		runnable.run();
		
		
	}
	
	@Override
	protected void onPause() {
		unbindService(serviceConnection);
		
		LocalBroadcastManager.getInstance(this).unregisterReceiver(myPositionChangesMessageReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(otherPositionMessageReceiver);
		
		super.onPause();
		
		handler.removeCallbacks(runnable);
	}
	
	@Override
	protected void onResume() {
		bindService(new Intent(MapActivity.this, IMService.class), serviceConnection, Context.BIND_AUTO_CREATE);
		
		LocalBroadcastManager.
		getInstance(this).
		registerReceiver(myPositionChangesMessageReceiver, new IntentFilter(IAppManager.INTENT_ACTION_USER_POSITION_CHANGED));
		
		LocalBroadcastManager.
		getInstance(this).
		registerReceiver(otherPositionMessageReceiver, new IntentFilter(IAppManager.INTENT_ACTION_OTHER_POSITION_CHANGED));
		
		
		runnable.run();
		
		super.onResume();
	}
	
	private void updateMyPositionOnTheMap() {
		if (TemporaryStorage.myInfo == null)
			return;
		GeoPoint myPos = new GeoPoint(TemporaryStorage.myInfo.getLatitude(), TemporaryStorage.myInfo.getLongitude());
		
		myPositionOverlay.removeAllItems();
		
		myPositionOverlay.addItem(new OverlayItem(TemporaryStorage.myInfo.getUsername(), "self", myPos));
		
		mMapView.getOverlays().set(0, myPositionOverlay);
		mMapView.invalidate();
		
		mMapController.setCenter(myPos);
		mMapController.animateTo(myPos);
	}
	
	private void updateOthersPositionOnTheMap() {
		othersPositionOverlay.removeAllItems();
		
		
		for (UserInfo u : TemporaryStorage.user_list) {
			OverlayItem temp = new OverlayItem(u.getUsername(), u.getUsername(), new GeoPoint(u.getLatitude(), u.getLongitude(), u.getAltitude()));
			
			if (u.isOnline()) {
				temp.setMarker(getResources().getDrawable(R.drawable.ic_status_online));
			} else {
				temp.setMarker(getResources().getDrawable(R.drawable.ic_status_offline));
			}
			
			othersPositionOverlay.addItem(temp);
		}
		
		mMapView.getOverlays().set(1, othersPositionOverlay);
		mMapView.invalidate();
	}

	private void initializeMyPositionOverlay() {
		ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gesture_listner = new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
			@Override
			public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
				if (item == null)
					return false;
				Toast.makeText(
						MapActivity.this, "Item '" + item.mTitle + "' (index=" + index + ") got single tapped up", Toast.LENGTH_LONG).show();

				return true; // We 'handled' this event.
			}

			@Override
			public boolean onItemLongPress(final int index, final OverlayItem item) {
				if (item == null)
					return false;
				Toast.makeText(MapActivity.this, "Item '" + item.mTitle + "' (index=" + index + ") got long pressed", Toast.LENGTH_LONG).show();
				
				return false;
			}
		};
		
		myPositionOverlay = new ItemizedIconOverlay<OverlayItem>(this, new ArrayList<OverlayItem>(), gesture_listner);	
		mMapView.getOverlays().add(myPositionOverlay);
	}
	
	private void initializeOtherPositionOverlay() {
		ItemizedIconOverlay.OnItemGestureListener<OverlayItem> gesture_listner = new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
			@Override
			public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
				if (item == null)
					return false;
				
				Toast.makeText(MapActivity.this, "Item '" + item.mTitle + "' (index=" + index + ") got single tapped up", Toast.LENGTH_LONG).show();

				return true; // We 'handled' this event.
			}

			@Override
			public boolean onItemLongPress(final int index, final OverlayItem item) {
				if (item == null)
					return false;
				
				Toast.makeText(MapActivity.this, "Item '" + item.mTitle + "' (index=" + index + ") got long pressed", Toast.LENGTH_LONG).show();
				
				return false;
			}
		};
		othersPositionOverlay = new ItemizedIconOverlay<OverlayItem>(this, new ArrayList<OverlayItem>(), gesture_listner);
		
		
		
		mMapView.getOverlays().add(othersPositionOverlay);
	}
	
	
	
	private BroadcastReceiver myPositionChangesMessageReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();
	        
	        
	        if (!action.equals(IAppManager.INTENT_ACTION_USER_POSITION_CHANGED))
	        	return;
	        
	        updateMyPositionOnTheMap();
	        
	        if (MainActivity.DEBUG)
	        	Toast.makeText(MapActivity.this, "received broadcasted intent!: "+action, Toast.LENGTH_LONG).show();
	        
	    }
	};
	
	
	private BroadcastReceiver otherPositionMessageReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	        String action = intent.getAction();	        
	        
	        if (!action.equals(IAppManager.INTENT_ACTION_OTHER_POSITION_CHANGED))
	        	return;
	        
	        updateOthersPositionOnTheMap();
	        
	        if (MainActivity.DEBUG)
	        	Toast.makeText(MapActivity.this, "received broadcasted intent!: "+action, Toast.LENGTH_LONG).show();
	        
	    }
	};
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_logged_user, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.menu_logout:
	        iMService.exit();
	        startActivity(new Intent(MapActivity.this, MainActivity.class));
	        MapActivity.this.finish();
	        return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}

}
