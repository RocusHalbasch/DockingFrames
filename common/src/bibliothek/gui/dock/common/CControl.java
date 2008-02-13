/*
 * Bibliothek - DockingFrames
 * Library built on Java/Swing, allows the user to "drag and drop"
 * panels containing any Swing-Component the developer likes to add.
 * 
 * Copyright (C) 2007 Benjamin Sigg
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 * Benjamin Sigg
 * benjamin_sigg@gmx.ch
 * CH - Switzerland
 */
package bibliothek.gui.dock.common;

import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;

import javax.swing.JFrame;
import javax.swing.KeyStroke;

import bibliothek.extension.gui.dock.theme.SmoothTheme;
import bibliothek.extension.gui.dock.theme.eclipse.EclipseTabDockAction;
import bibliothek.gui.DockController;
import bibliothek.gui.DockFrontend;
import bibliothek.gui.DockStation;
import bibliothek.gui.Dockable;
import bibliothek.gui.dock.DockElement;
import bibliothek.gui.dock.ScreenDockStation;
import bibliothek.gui.dock.action.ActionGuard;
import bibliothek.gui.dock.action.DockAction;
import bibliothek.gui.dock.action.DockActionSource;
import bibliothek.gui.dock.common.intern.*;
import bibliothek.gui.dock.event.DockAdapter;
import bibliothek.gui.dock.facile.action.CloseAction;
import bibliothek.gui.dock.facile.action.StateManager;
import bibliothek.gui.dock.frontend.Setting;
import bibliothek.gui.dock.layout.DockSituationIgnore;
import bibliothek.gui.dock.support.util.ApplicationResource;
import bibliothek.gui.dock.support.util.ApplicationResourceManager;
import bibliothek.gui.dock.themes.NoStackTheme;
import bibliothek.gui.dock.util.PropertyKey;
import bibliothek.gui.dock.util.PropertyValue;
import bibliothek.util.xml.XElement;

/**
 * Manages the interaction between {@link SingleCDockable}, {@link MultipleCDockable}
 * and the {@link CContentArea}.<br>
 * Clients should call <code>read</code> and <code>write</code> of the
 * {@link ApplicationResourceManager}, accessible through {@link #getResources()}, 
 * to store or load the configuration.<br>
 * Clients which do no longer need a {@link CControl} can call {@link #destroy()}
 * to free resources.
 * @author Benjamin Sigg
 *
 */
public class CControl {
    /**
     * {@link KeyStroke} used to change a {@link CDockable} into maximized-state,
     * or to go out of maximized-state when needed.
     */
    public static final PropertyKey<KeyStroke> KEY_MAXIMIZE_CHANGE = 
        new PropertyKey<KeyStroke>( "fcontrol.maximize_change" );
    
    /**
     * {@link KeyStroke} used to change a {@link CDockable} into
     * maximized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_MAXIMIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_maximized" );
    
    /**
     * {@link KeyStroke} used to change a {@link CDockable} into
     * normalized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_NORMALIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_normalized" );
    
    /**
     * {@link KeyStroke} used to change a {@link CDockable} into
     * minimized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_MINIMIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_minimized" );
    
    /**
     * {@link KeyStroke} used to change a {@link CDockable} into
     * externalized-state.
     */
    public static final PropertyKey<KeyStroke> KEY_GOTO_EXTERNALIZED =
        new PropertyKey<KeyStroke>( "fcontrol.goto_externalized" );
    
    /**
     * {@link KeyStroke} used to close a {@link CDockable}.
     */
    public static final PropertyKey<KeyStroke> KEY_CLOSE = 
        new PropertyKey<KeyStroke>( "fcontrol.close" );
    
    /** the unique id of the station that handles the externalized dockables */
    public static final String EXTERNALIZED_STATION_ID = "external";
    
    /** the unique id of the default-{@link CContentArea} created by this control */
    public static final String CONTENT_AREA_STATIONS_ID = "fcontrol";
    
    /** connection to the real DockingFrames */
	private DockFrontend frontend;
	
	/** the set of known factories */
	private Map<String, FactoryProperties> factories = 
		new HashMap<String, FactoryProperties>();
	
	/** list of all dockables registered to this control */
	private List<CDockable> dockables =
	    new ArrayList<CDockable>();
	
	/** list of all {@link SingleCDockable}s */
	private List<SingleCDockable> singleDockables =
	    new ArrayList<SingleCDockable>();
	
	/** the set of {@link MultipleCDockable}s */
	private List<MultipleCDockable> multiDockables = 
		new ArrayList<MultipleCDockable>();
	
	/** access to internal methods of some {@link CDockable}s */
	private Map<CDockable, CDockableAccess> accesses = new HashMap<CDockable, CDockableAccess>();
	
	/** a manager allowing the user to change the extended-state of some {@link CDockable}s */
	private CStateManager stateManager;
	
	/** the default location of newly opened {@link CDockable}s */
	private CLocation defaultLocation;
	
	/** the center component of the main-frame */
	private CContentArea content;
	
	/** the whole list of contentareas known to this control, includes {@link #content} */
	private List<CContentArea> contents = new ArrayList<CContentArea>();
	
	/** Access to the internal methods of this control */
	private CControlAccess access = new Access();
	
	/** manager used to store and read configurations */
	private ApplicationResourceManager resources = new ApplicationResourceManager();
	
	/** a list of listeners which are to be informed when this control is no longer in use */
	private List<DestroyHook> hooks = new ArrayList<DestroyHook>();
	
	/** factory used to create new elements for this control */
	private CControlFactory factory;

    /**
     * Creates a new control
     * @param frame the main frame of the application, needed to create
     * dialogs for externalized {@link CDockable}s
     */
    public CControl( JFrame frame ){
        this( frame, false );
    }
	
	/**
     * Creates a new control
     * @param frame the main frame of the application, needed to create
     * dialogs for externalized {@link CDockable}s
     * @param restrictedEnvironment whether this application runs in a
     * restricted environment and is not allowed to listen for global events.
     */
    public CControl( JFrame frame, boolean restrictedEnvironment ){
        this( frame, restrictedEnvironment ? new SecureControlFactory() : new EfficientControlFactory() );
    }
	
	/**
	 * Creates a new control
	 * @param frame the main frame of the application, needed to create
	 * dialogs for externalized {@link CDockable}s
	 * @param factory a factory which is used to create new elements for this
	 * control.
	 */
	public CControl( JFrame frame, CControlFactory factory ){
	    this.factory = factory;
	    
	    DockController controller = factory.createController();
	    controller.setSingleParentRemover( new CSingleParentRemover() );

		frontend = new DockFrontend( controller, frame ){
		    @Override
		    protected Setting createSetting() {
		        CSetting setting = new CSetting();
		        setting.setModes(
		                new StateManager.StateManagerSetting<StateManager.Location>( 
		                        new StateManager.LocationConverter() ) );
		        return setting;
		    }
		    
		    @Override
		    public Setting getSetting( boolean entry ) {
		        CSetting setting = (CSetting)super.getSetting( entry );
		        setting.setModes( stateManager.getSetting( new StateManager.LocationConverter() ) );
		        return setting;
		    }
		    
		    @Override
		    public void setSetting( Setting setting, boolean entry ) {
		        if( entry ){
                    stateManager.normalizeAllWorkingAreaChildren();
                }
		        
		        super.setSetting( setting, entry );
		        stateManager.setSetting( ((CSetting)setting).getModes() );
		    }
		};
		frontend.setIgnoreForEntry( new DockSituationIgnore(){
		    public boolean ignoreChildren( DockStation station ) {
		        Dockable dockable = station.asDockable();
		        if( dockable == null )
		            return false;
		        if( dockable instanceof CommonDockable ){
		            CDockable fdockable = ((CommonDockable)dockable).getDockable();
		            if( fdockable instanceof CWorkingArea )
		                return true;
		        }
		        return false;
		    }
		    public boolean ignoreElement( DockElement element ) {
		        if( element instanceof CommonDockable ){
		            CDockable fdockable = ((CommonDockable)element).getDockable();
		            if( fdockable.getWorkingArea() != null )
		                return true;
		        }
		        return false;
		    }
		});
		frontend.setShowHideAction( false );
		frontend.getController().setTheme( new NoStackTheme( new SmoothTheme() ) );
		frontend.getController().addActionGuard( new ActionGuard(){
		    public boolean react( Dockable dockable ) {
		        return dockable instanceof CommonDockable;
		    }
		    public DockActionSource getSource( Dockable dockable ) {
		        return ((CommonDockable)dockable).getDockable().getClose();
		    }
		});
		frontend.getController().getRegister().addDockRegisterListener( new DockAdapter(){
		    @Override
		    public void dockableRegistered( DockController controller, Dockable dockable ) {
		        if( dockable instanceof CommonDockable ){
		            CDockableAccess access = accesses.get( ((CommonDockable)dockable).getDockable() );
		            if( access != null ){
		                access.informVisibility( true );
		            }
		        }
		    }
		    
		    @Override
		    public void dockableUnregistered( DockController controller, Dockable dockable ) {
		        if( dockable instanceof CommonDockable ){
		            CDockable fdock = ((CommonDockable)dockable).getDockable();
                    CDockableAccess access = accesses.get( fdock );
                    if( access != null ){
                        access.informVisibility( false );
                    }
                    if( fdock instanceof MultipleCDockable ){
                        MultipleCDockable multiple = (MultipleCDockable)fdock;
                        if( multiple.isRemoveOnClose() ){
                            remove( multiple );
                        }
                    }
                }
		    }
		});
		
		frontend.getController().addAcceptance( new StackableAcceptance() );
		frontend.getController().addAcceptance( new WorkingAreaAcceptance( access ) );
		frontend.getController().addAcceptance( new ExtendedModeAcceptance( access ) );
		
		try{
    		resources.put( "fcontrol.frontend", new ApplicationResource(){
    		    public void write( DataOutputStream out ) throws IOException {
                    writeWorkingAreas( out );
    		        frontend.write( out );
    		    }
    		    public void read( DataInputStream in ) throws IOException {
    		        readWorkingAreas( in );
    		        frontend.read( in );
    		    }
    		    public void writeXML( XElement element ) {
    		        writeWorkingAreasXML( element.addElement( "areas" ) );
    		        frontend.writeXML( element.addElement( "frontend" ) );
    		    }
    		    public void readXML( XElement element ) {
    		        readWorkingAreasXML( element.getElement( "areas" ) );
    		        frontend.readXML( element.getElement( "frontend" ) );
    		    }
    		});
		}
		catch( IOException ex ){
		    System.err.println( "Non lethal IO-error:" );
		    ex.printStackTrace();
		}
		
		stateManager = new CStateManager( access );
		content = createContentArea( CONTENT_AREA_STATIONS_ID );
		frontend.setDefaultStation( content.getCenter() );
		
		final ScreenDockStation screen = factory.createScreenDockStation( frame );
		stateManager.add( EXTERNALIZED_STATION_ID, screen );
		frontend.addRoot( screen, EXTERNALIZED_STATION_ID );
		screen.setShowing( frame.isVisible() );
		frame.addComponentListener( new ComponentListener(){
		    public void componentShown( ComponentEvent e ) {
		        screen.setShowing( true );
		    }
		    public void componentHidden( ComponentEvent e ) {
		        screen.setShowing( false );
		    }
		    public void componentMoved( ComponentEvent e ) {
		        // ignore
		    }
		    public void componentResized( ComponentEvent e ) {
		        // ignore
		    }
		});
		
		// set some default values
		putProperty( KEY_MAXIMIZE_CHANGE, KeyStroke.getKeyStroke( KeyEvent.VK_M, InputEvent.CTRL_MASK ) );
		putProperty( KEY_GOTO_EXTERNALIZED, KeyStroke.getKeyStroke( KeyEvent.VK_E, InputEvent.CTRL_MASK ) );
		putProperty( KEY_GOTO_NORMALIZED, KeyStroke.getKeyStroke( KeyEvent.VK_N, InputEvent.CTRL_MASK ) );
		putProperty( KEY_CLOSE, KeyStroke.getKeyStroke( KeyEvent.VK_C, InputEvent.CTRL_MASK ) );
	}
	
	/**
	 * Writes a map using the unique identifiers of each {@link SingleCDockable} to
	 * tell to which {@link CWorkingArea} it belongs.
	 * @param out the stream to write into
	 * @throws IOException if an I/O error occurs
	 */
	private void writeWorkingAreas( DataOutputStream out ) throws IOException{
	    Map<String,String> map = new HashMap<String, String>();
	    
	    for( SingleCDockable dockable : singleDockables ){
	        CWorkingArea area = dockable.getWorkingArea();
	        if( area != null ){
	            map.put( dockable.getUniqueId(), area.getUniqueId() );
	        }
	    }
	    
	    out.writeInt( map.size() );
	    for( Map.Entry<String, String> entry : map.entrySet() ){
	        out.writeUTF( entry.getKey() );
	        out.writeUTF( entry.getValue() );
	    }
	}
	
	/**
	 * Writes a map of all {@link SingleCDockable}s and their {@link CWorkingArea}.
	 * @param element the element to write into
	 */
	private void writeWorkingAreasXML( XElement element ){
	    for( SingleCDockable dockable : singleDockables ){
	        CWorkingArea area = dockable.getWorkingArea();
	        if( area != null ){
	            XElement xarea = element.addElement( "area" );
	            xarea.addString( "id", area.getUniqueId() );
	            xarea.addString( "child", dockable.getUniqueId() );
	        }
	    }
	}
	
	/**
	 * Reads a map telling for each {@link SingleCDockable} to which {@link CWorkingArea}
	 * it belongs.
	 * @param in the stream to read from
	 * @throws IOException if an I/O error occurs
	 */
	private void readWorkingAreas( DataInputStream in ) throws IOException{
	    Map<String, SingleCDockable> dockables = new HashMap<String, SingleCDockable>();
	    Map<String, CWorkingArea> areas = new HashMap<String, CWorkingArea>();
	    
	    for( SingleCDockable dockable : this.singleDockables ){
	        if( dockable instanceof CWorkingArea ){
	            CWorkingArea area = (CWorkingArea)dockable;
	            dockables.put( area.getUniqueId(), area );
	            areas.put( area.getUniqueId(), area );
	        }
	        else{
	            dockables.put( dockable.getUniqueId(), dockable );
	        }
	    }
	    
	    for( int i = 0, n = in.readInt(); i<n; i++ ){
	        String key = in.readUTF();
	        String value = in.readUTF();
	        
	        CDockable dockable = dockables.get( key );
	        if( dockable != null ){
	            CWorkingArea area = areas.get( value );
	            dockable.setWorkingArea( area );
	        }
	    }
	}
	
	   /**
     * Reads a map telling for each {@link SingleCDockable} to which {@link CWorkingArea}
     * it belongs.
     * @param element the xml element to read from
     */
    private void readWorkingAreasXML( XElement element ){
        Map<String, SingleCDockable> dockables = new HashMap<String, SingleCDockable>();
        Map<String, CWorkingArea> areas = new HashMap<String, CWorkingArea>();
        
        for( SingleCDockable dockable : this.singleDockables ){
            if( dockable instanceof CWorkingArea ){
                CWorkingArea area = (CWorkingArea)dockable;
                dockables.put( area.getUniqueId(), area );
                areas.put( area.getUniqueId(), area );
            }
            else{
                dockables.put( dockable.getUniqueId(), dockable );
            }
        }
        
        for( XElement xarea : element.getElements( "area" )){
            String key = xarea.getString( "child" );
            String value = xarea.getString( "id" );
            
            CDockable dockable = dockables.get( key );
            if( dockable != null ){
                CWorkingArea area = areas.get( value );
                dockable.setWorkingArea( area );
            }
        }
    }
	
	/**
	 * Frees as much resources as possible. This {@link CControl} will no longer
	 * work correctly after this method was called.
	 */
	public void destroy(){
	    frontend.getController().kill();
	    for( DestroyHook hook : hooks )
	        hook.destroy();
	}
	
	/**
	 * Creates and adds a new {@link CWorkingArea} to this control. The area
	 * is not made visible by this method.
	 * @param uniqueId the unique id of the area
	 * @return the new area
	 */
	public CWorkingArea createWorkingArea( String uniqueId ){
	    CWorkingArea area = factory.createWorkingArea( uniqueId );
	    add( area );
	    return area;
	}
	
	/**
	 * Creates and adds a new {@link CContentArea}.
	 * @param uniqueId the unique id of the new contentarea, the id must be unique
	 * in respect to all other contentareas which are registered at this control.
	 * @return the new contentarea
	 * @throws IllegalArgumentException if the id is not unique
	 * @throws NullPointerException if the id is <code>null</code>
	 */
	public CContentArea createContentArea( String uniqueId ){
		if( uniqueId == null )
			throw new NullPointerException( "uniqueId must not be null" );
		
		for( CContentArea center : contents ){
			if( center.getUniqueId().equals( uniqueId ))
				throw new IllegalArgumentException( "There exists already a CContentArea with the unique id " + uniqueId );
		}
		
		CContentArea center = new CContentArea( access, uniqueId );
		contents.add( center );
		return center;
	}
	
	/**
	 * Removes <code>content</code> from the list of known contentareas. This also removes
	 * the stations of <code>content</code> from this control. Elements aboard the
	 * stations are made invisible, but not removed from this control.
	 * @param content the contentarea to remove
	 * @throws IllegalArgumentException if the default-contentarea equals <code>content</code>
	 */
	public void removeContentArea( CContentArea content ){
		if( this.content == content )
			throw new IllegalArgumentException( "The default-contentarea can't be removed" );
		
		if( contents.remove( content ) ){
			frontend.removeRoot( content.getCenter() );
			frontend.removeRoot( content.getEast() );
			frontend.removeRoot( content.getWest() );
			frontend.removeRoot( content.getNorth() );
			frontend.removeRoot( content.getSouth() );
			
			stateManager.remove( content.getCenterIdentifier() );
			stateManager.remove( content.getEastIdentifier() );
			stateManager.remove( content.getWestIdentifier() );
			stateManager.remove( content.getNorthIdentifier() );
			stateManager.remove( content.getSouthIdentifier() );
		}
	}
	
	/**
	 * Gets an unmodifiable list of all {@link CContentArea}s registered at
	 * this control
	 * @return the list of contentareas
	 */
	public List<CContentArea> getContentAreas(){
		return Collections.unmodifiableList( contents );
	}
	
	/**
	 * Gets the factory which is mainly used to create new elements for this
	 * control.
	 * @return the factory
	 */
	public CControlFactory getFactory() {
        return factory;
    }

	/**
	 * Adds a destroy-hook. The hook is called when this {@link CControl} is
	 * destroyed through {@link #destroy()}.
	 * @param hook the new hook
	 */
	public void addDestroyHook( DestroyHook hook ){
	    if( hook == null )
	        throw new NullPointerException( "hook must not be null" );
	    hooks.add( hook );
	}
	
	/**
	 * Removes a destroy-hook from this {@link CControl}.
	 * @param hook the hook to remove
	 */
	public void removeDestroyHook( DestroyHook hook ){
	    hooks.remove( hook );
	}
	
	/**
	 * Grants access to the manager that reads and stores configurations
	 * of the facile-framework.<br>
	 * Clients can add their own {@link ApplicationResource}s to this manager,
	 * however clients are strongly discouraged from removing {@link ApplicationResource}
	 * which they did not add by themself.
	 * @return the persistent storage
	 */
	public ApplicationResourceManager getResources() {
        return resources;
    }
	
	/**
	 * Changes the value of a property. Some properties are:
	 * <ul>
	 * <li>{@link #KEY_MAXIMIZE_CHANGE}</li>
	 * <li>{@link #KEY_GOTO_EXTERNALIZED}</li>
	 * <li>{@link #KEY_GOTO_MAXIMIZED}</li>
	 * <li>{@link #KEY_GOTO_MINIMIZED}</li>
	 * <li>{@link #KEY_GOTO_NORMALIZED}</li>
	 * <li>{@link #KEY_CLOSE}</li>
	 * </ul>
	 * @param <A> the type of the value
	 * @param key the name of the property
	 * @param value the new value, can be <code>null</code>
	 */
	public <A> void putProperty( PropertyKey<A> key, A value ){
	    frontend.getController().getProperties().set( key, value );
	}
	
	/**
	 * Gets the value of a property.
	 * @param <A> the type of the property
	 * @param key the name of the property
	 * @return the value or <code>null</code>
	 */
	public <A> A getProperty( PropertyKey<A> key ){
	    return frontend.getController().getProperties().get( key );
	}
	
	/**
	 * Gets the element that should be in the center of the mainframe.
	 * @return the center of the mainframe of the application
	 */
	public CContentArea getContentArea() {
        return content;
    }
	
	/**
	 * Adds a dockable to this control. The dockable can be made visible afterwards.
	 * @param <F> the type of the new element
	 * @param dockable the new element to show
	 * @return <code>dockable</code>
	 */
	public <F extends SingleCDockable> F add( F dockable ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() != null )
			throw new IllegalStateException( "dockable is already part of a control" );

		dockable.setControl( access );
		String id = "single " + dockable.getUniqueId();
		accesses.get( dockable ).setUniqueId( id );
		frontend.add( dockable.intern(), id );
		frontend.setHideable( dockable.intern(), true );
		dockables.add( dockable );
		singleDockables.add( dockable );
		return dockable;
	}
	
	/**
	 * Adds a dockable to this control. The dockable can be made visible afterwards.
	 * @param <F> the type of the new element
	 * @param dockable the new element to show
	 * @return <code>dockable</code>
	 */
	public <F extends MultipleCDockable> F add( F dockable ){
	    String factory = access.getFactoryId( dockable.getFactory() );
        if( factory == null ){
            throw new IllegalStateException( "the factory for a MultipleDockable is not registered" );
        }
        
        int count = 0;
        for( MultipleCDockable multi : multiDockables ){
            if( factory.equals( access.getFactoryId( multi.getFactory() )))
                count++;
        }
        String id = "multi " + count + " " + factory;
        return add( dockable, id );
	}

	/**
     * Adds a dockable to this control. The dockable can be made visible afterwards.
     * @param <F> the type of the new element
     * @param dockable the new element to show
     * @param uniqueId id the unique id of the new element
     * @return <code>dockable</code>
     */
	private <F extends MultipleCDockable> F add( F dockable, String uniqueId ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() != null )
			throw new IllegalStateException( "dockable is already part of a control" );
		
		dockable.setControl( access );
		accesses.get( dockable ).setUniqueId( uniqueId );
		multiDockables.add( dockable );
		dockables.add( dockable );
		return dockable;
	}
	
	/**
	 * Removes a dockable from this control. The dockable is made invisible.
	 * @param dockable the element to remove
	 */
	public void remove( SingleCDockable dockable ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() == access ){
			dockable.setVisible( false );
			frontend.remove( dockable.intern() );
			dockables.remove( dockable );
			singleDockables.remove( dockable );
			dockable.setControl( null );
		}
	}
	
	/**
	 * Removes a dockable from this control. The dockable is made invisible.
	 * @param dockable the element to remove
	 */
	public void remove( MultipleCDockable dockable ){
		if( dockable == null )
			throw new NullPointerException( "dockable must not be null" );
		
		if( dockable.getControl() == access ){
			dockable.setVisible( false );
			frontend.remove( dockable.intern() );
			multiDockables.remove( dockable );
			dockables.remove( dockable );
			String factory = access.getFactoryId( dockable.getFactory() );
			
			if( factory == null ){
				throw new IllegalStateException( "the factory for a MultipleDockable is not registered" );
			}
			
			factories.get( factory ).count--;
			dockable.setControl( null );
		}
	}
	
	/**
	 * Gets the number of {@link CDockable}s that are registered in this
	 * {@link CControl}.
	 * @return the number of dockables
	 */
	public int getFDockableCount(){
	    return dockables.size();
	}
	
	/**
	 * Gets the index'th dockable that is registered in this control
	 * @param index the index of the element
	 * @return the selected dockable
	 */
	public CDockable getFDockable( int index ){
	    return dockables.get( index );
	}
	
	/**
	 * Adds a factory to this control. The factory will create {@link MultipleCDockable}s
	 * when a layout is loaded.
	 * @param id the unique id of the factory
	 * @param factory the new factory
	 */
	public void add( final String id, final MultipleCDockableFactory<?,?> factory ){
		if( id == null )
			throw new NullPointerException( "id must not be null" );
		
		if( factory == null )
			throw new NullPointerException( "factory must not be null" );
		
		if( factories.containsKey( id )){
			throw new IllegalArgumentException( "there is already a factory named " + id );
		}
		
		FactoryProperties properties = new FactoryProperties();
		properties.factory = factory;
		
		factories.put( id, properties );
		
		frontend.registerFactory( new CommonDockableFactory( id, factory, access ) );
	}
	
	/**
	 * Sets the location where {@link CDockable}s are opened when there is
	 * nothing else specified for these <code>CDockable</code>s.
	 * @param defaultLocation the location, can be <code>null</code>
	 */
	public void setDefaultLocation( CLocation defaultLocation ){
		this.defaultLocation = defaultLocation;
	}
	
	/**
	 * Gets the location where {@link CDockable}s are opened when nothing else
	 * is specified.
	 * @return the location, might be <code>null</code>
	 * @see #setDefaultLocation(CLocation)
	 */
	public CLocation getDefaultLocation(){
		return defaultLocation;
	}
	
	/**
	 * Sets the {@link CMaximizeBehavior}. The behavior decides what happens
	 * when the user want's to maximize or to un-maximize a {@link CDockable}.
	 * @param behavior the new behavior, not <code>null</code>
	 */
	public void setMaximizeBehavior( CMaximizeBehavior behavior ){
		stateManager.setMaximizeBehavior( behavior );
	}
	
	/**
	 * Gets the currently used maximize-behavior.
	 * @return the behavior, not <code>null</code>
	 * @see #setMaximizeBehavior(CMaximizeBehavior)
	 */
	public CMaximizeBehavior getMaximizeBehavior(){
		return stateManager.getMaximizeBehavior();
	}
	
	/**
	 * Gets the representation of the layer beneath the facile-layer.
	 * @return the entry point to DockingFrames
	 */
	public DockFrontend intern(){
		return frontend;
	}
	
	/**
	 * Writes the current and all known layouts into <code>file</code>.
	 * @param file the file to override
	 * @throws IOException if the file can't be written
	 */
	public void write( File file ) throws IOException{
		DataOutputStream out = new DataOutputStream( new BufferedOutputStream( new FileOutputStream( file )));
		write( out );
		out.close();
	}
	
	/**
	 * Writes the current and all known layouts into <code>out</code>.
	 * @param out the stream to write into
	 * @throws IOException if the stream is not writable
	 */
	public void write( DataOutputStream out ) throws IOException{
		out.writeInt( 1 );
		frontend.write( out );
	}
	
	/**
	 * Reads the current and other known layouts from <code>file</code>.
	 * @param file the file to read from
	 * @throws IOException if the file can't be read
	 */
	public void read( File file ) throws IOException{
		DataInputStream in = new DataInputStream( new BufferedInputStream( new FileInputStream( file )));
		read( in );
		in.close();
	}
	
	/**
	 * Reads the current and other known layouts from <code>in</code>.
	 * @param in the stream to read from
	 * @throws IOException if the stream can't be read
	 */
	public void read( DataInputStream in ) throws IOException{
		int version = in.readInt();
		if( version != 1 )
			throw new IOException( "Version of stream unknown, expected 1 but found: " + version );
		
		frontend.read( in );
	}
	
	/**
	 * Stores the current layout with the given name.
	 * @param name the name of the current layout.
	 */
	public void save( String name ){
		frontend.save( name );
	}
	
	/**
	 * Loads an earlier stored layout.
	 * @param name the name of the layout.
	 */
	public void load( String name ){
		frontend.load( name );
	}
	
	/**
	 * Deletes a layout that has been stored earlier.
	 * @param name the name of the layout to delete
	 */
	public void delete( String name ){
		frontend.delete( name );
	}
	
	/**
	 * Gets a list of all layouts that are currently known.
	 * @return the list of layouts
	 */
	public String[] layouts(){
		Set<String> settings = frontend.getSettings();
		return settings.toArray( new String[ settings.size() ] );
	}
	
	
	/**
	 * Properties associated with one factory.
	 * @author Benjamin Sigg
	 *
	 */
	private static class FactoryProperties{
		/** the associated factory */
		public MultipleCDockableFactory<?,?> factory;
		/** the number of {@link MultipleCDockable} that belong to {@link #factory} */
		public int count = 0;
	}
	
	/**
	 * A class giving access to the internal methods of the enclosing
	 * {@link CControl}.
	 * @author Benjamin Sigg
	 */
	private class Access implements CControlAccess{
	    /** action used to close {@link CDockable}s  */
	    private CCloseAction closeAction;
	    
	    public CControl getOwner(){
			return CControl.this;
		}
	    
	    public <F extends MultipleCDockable> F add( F dockable, String uniqueId ) {
	        return CControl.this.add( dockable, uniqueId );
	    }
	    
	    public void link( CDockable dockable, CDockableAccess access ) {
	        if( access == null )
	            accesses.remove( dockable );
	        else{
	            accesses.put( dockable, access );
	        }
	    }
	    
	    public CDockableAccess access( CDockable dockable ) {
	        return accesses.get( dockable );
	    }
		
		public void hide( CDockable dockable ){
			frontend.hide( dockable.intern() );
		}
		
		public void show( CDockable dockable ){
			CDockableAccess access = access( dockable );
			CLocation location = null;
			if( access != null ){
				location = access.internalLocation();
			}
			if( location == null ){
				if( !frontend.hasLocation( dockable.intern() ))
					location = defaultLocation;
			}
			
			if( location == null ){
			    frontend.show( dockable.intern() );
			}
			else{
				stateManager.setLocation( dockable.intern(), location );
			}
			stateManager.ensureValidLocation( dockable );
		}
		
		public boolean isVisible( CDockable dockable ){
			return frontend.isShown( dockable.intern() );
		}
		
		public String getFactoryId( MultipleCDockableFactory<?,?> factory ){
			for( Map.Entry<String, FactoryProperties> entry : factories.entrySet() ){
				if( entry.getValue().factory == factory )
					return entry.getKey();
			}
			
			return null;
		}
		
		public CStateManager getStateManager() {
		    return stateManager;
		}
		
		public DockAction createCloseAction( final CDockable fdockable ) {
		    if( closeAction == null )
		        closeAction = new CCloseAction();
		    
		    return closeAction;
		}
	}
	
	/**
	 * Action that can close {@link CDockable}s
	 * @author Benjamin Sigg
	 */
	@EclipseTabDockAction
	private class CCloseAction extends CloseAction{
	    /**
	     * Creates a new action
	     */
	    public CCloseAction(){
	        super( frontend.getController() );
	        new PropertyValue<KeyStroke>( KEY_CLOSE, frontend.getController() ){
	            @Override
	            protected void valueChanged( KeyStroke oldValue, KeyStroke newValue ) {
	                setAccelerator( newValue );
	            }
	        };
	    }
	    
	    @Override
        protected void close( Dockable dockable ) {
	        CDockable fdockable = ((CommonDockable)dockable).getDockable();
	        if( fdockable.getExtendedMode() == CDockable.ExtendedMode.MAXIMIZED )
	            fdockable.setExtendedMode( CDockable.ExtendedMode.NORMALIZED );
	        fdockable.setVisible( false );
        }
	}
}