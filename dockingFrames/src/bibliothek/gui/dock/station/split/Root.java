/**
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

package bibliothek.gui.dock.station.split;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;

import javax.swing.border.Border;

import bibliothek.gui.Dockable;
import bibliothek.gui.dock.SplitDockStation;
import bibliothek.gui.dock.station.split.SplitDockTree.Key;

/**
 * The root of the tree that represents the internal structure of a 
 * {@link SplitDockStation}.
 * @author Benjamin Sigg
 */
public class Root extends SplitNode{
	/** the single child of this root */
    private SplitNode child;
    
    /** space around the root that is left free */
    private Insets insets = new Insets( 2, 2, 2, 2 );
    
    /**
     * Creates a new root.
     * @param access the access to internal methods of the
     * {@link SplitDockStation}, must not be <code>null</code>
     */
    public Root( SplitDockAccess access ){
        super( access );
    }
    
    /**
     * Sets the child of this root. Every root has only one child.
     * @param child the child of the root, can be <code>null</code>
     */
    public void setChild( SplitNode child ){
        this.child = child;
        if( child != null )
            child.setParent( this );
    }
    
    @Override
    public void replace( SplitNode old, SplitNode child ) {
        if( this.child != old )
            throw new IllegalArgumentException( "unknown child " + old );
        setChild( child );
    }
    
    /**
     * Gets the child of this root.
     * @return the child or <code>null</code>
     * @see #setChild(SplitNode)
     */
    public SplitNode getChild() {
        return child;
    }
    
    @Override
    protected Root getRoot() {
        return this;
    }
    
    @Override
    public Dimension getMinimumSize() {
    	if( child == null )
    		return new Dimension( 0, 0 );
    	return child.getMinimumSize();
    }
    
    /**
     * Updates all locations and sizes of the {@link Component Components}
     * which are in the structure of this tree.
     */
    public void updateBounds(){
        Insets insets = getInsets();
        SplitDockStation station = getAccess().getOwner();
        double factorW = station.getWidth() - insets.left - insets.right;
        double factorH = station.getHeight() - insets.top - insets.bottom;
        
        if( factorW < 0 || factorH < 0 ){
            updateBounds( 0, 0, 1.0, 1.0, factorW, factorH );
        }
        else{
            updateBounds( insets.left / factorW, insets.top / factorH, 
                1.0, 1.0, factorW, factorH );
        }
    }
    
    /**
     * Gets the factor which has to be multiplied with relative x coordinates
     * and widths to get their size in pixel.
     * @return the horizontal stretch factor
     */
    public double getWidthFactor(){
        return getAccess().getOwner().getWidth() - insets.left - insets.right;
    }
    
    /**
     * Gets the factor which has to be multiplied with a relative y coordinate
     * or height to get their size in pixel.
     * @return the vertical stretch factor
     */
    public double getHeightFactor(){
        return getAccess().getOwner().getHeight() - insets.top - insets.bottom;
    }
    
    /**
     * Gets the preferred operation when dragging the {@link Dockable}
     * <code>drop</code> to the location <code>x/y</code>.
     * @param x the x-coordinate of the mouse
     * @param y the y-coordinate of the mouse
     * @param drop the Dockable which will be dropped
     * @return where to trop the Dockable or <code>null</code>
     */
    public PutInfo getPut( int x, int y, Dockable drop ){
        double factorW = getWidthFactor();
        double factorH = getHeightFactor();
        
        return getPut( x, y, factorW, factorH, drop );           
    }
    
    /**
     * Tells whether the location x/y is in the override zone.
     * @param x the x-coordinate of the mouse
     * @param y the y-coordinate of the mouse
     * @return <code>true</code> if this station should have priority
     * over all other stations when the mouse is in x/y.
     */
    public boolean isInOverrideZone( int x, int y ){
        double factorW = getWidthFactor();
        double factorH = getHeightFactor();
        
        return isInOverrideZone( x, y, factorW, factorH );
    }
    
    /**
     * Gets the insets which will be free from any {@link Component}.
     * @return the insets
     */
    public Insets getInsets(){
        Border border = getAccess().getOwner().getBorder();
        if( border != null ){
            Insets in = border.getBorderInsets( getAccess().getOwner() );
            return new Insets( in.top + insets.top, in.left + insets.left,
                    in.bottom + insets.bottom, in.right + insets.right );
        }
        else
            return insets;
    }
    
    @Override
    public void evolve( Key key, boolean checkValidity ){
    	setChild( create( key, checkValidity ) );
    }
    
    @Override
    public boolean insert( SplitDockPathProperty property, int depth, Dockable dockable ) {
        if( child == null ){
            Leaf leaf = create( dockable );
            if( leaf == null )
                return false;
            setChild( leaf );
            return true;
        }
        else
            return child.insert( property, depth, dockable );
    }
    

    @Override
    public <N> N submit( SplitTreeFactory<N> factory ) {
        if( child == null )
            return factory.root( null );
        else
            return factory.root( child.submit( factory ) );
    }
    
    @Override
    public void updateBounds( double x, double y, double width, double height, double factorW, double factorH ) {
        super.updateBounds( x, y, width, height, factorW, factorH );
        if( child != null )
            child.updateBounds( x, y, width, height, factorW, factorH );
    }

    @Override
    public PutInfo getPut( int x, int y, double factorW, double factorH, Dockable drop ) {
        if( !getBounds().contains( x, y ))
            return null;
        
        if( child != null )
            return child.getPut( x, y, factorW, factorH, drop );
        else
            return null;
    }
    
    @Override
    public boolean isInOverrideZone( int x, int y, double factorW, double factorH ) {
        if( !getBounds().contains( x, y ))
            return false;
        
        if( child != null )
            return child.isInOverrideZone( x, y, factorW, factorH );
        else
            return false;
    }
    
    @Override
    public Leaf getLeaf( Dockable dockable ) {
        return child == null ? null : child.getLeaf( dockable );
    }
    
    @Override
    public Node getDividerNode( int x, int y ) {
        if( child == null )
            return null;
        else
            return child.getDividerNode( x, y );
    }

    @Override
    public void visit( SplitNodeVisitor visitor ) {
        visitor.handleRoot( this );
        if( child != null )
            child.visit( visitor );
    }
}