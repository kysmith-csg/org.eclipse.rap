/*******************************************************************************
 * Copyright (c) 2009 EclipseSource and others. All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   EclipseSource - initial API and implementation
 ******************************************************************************/
package org.eclipse.rap.examples.pages;

import java.util.*;
import java.util.List;

import org.eclipse.jface.viewers.*;
import org.eclipse.rap.examples.IExamplePage;
import org.eclipse.rap.examples.pages.Elements.Element;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;


public class TableViewerExample implements IExamplePage {

  private static final int NUMBER = 0;
  private static final int SYMBOL = 1;
  private static final int NAME = 2;

  private TableViewer viewer;
  private Label lblSelection;
  private final ElementsFilter viewerFilter;
  private final ElementsLabelProvider labelProvider;

  private List elements;

  public TableViewerExample() {
    viewerFilter = new ElementsFilter();
    labelProvider = new ElementsLabelProvider();
  }

  public void createControl( final Composite parent ) {

    parent.setLayout( ExampleUtil.createGridLayout( 1, false, 10, 20 ) );

    Composite composite = new Composite( parent, SWT.NONE );
    composite.setLayoutData( new GridData( SWT.FILL, SWT.FILL, true, true ) );
    composite.setLayout( new GridLayout( 2, false ) );

    Label lblFilter = new Label( composite, SWT.NONE );
    lblFilter.setText( "Filter" );
    Text txtFilter = new Text( composite, SWT.BORDER );
    txtFilter.setLayoutData( new GridData( SWT.FILL, SWT.TOP, true, false ) );
    txtFilter.addModifyListener( new ModifyListener() {

      public void modifyText( final ModifyEvent event ) {
        Text text = ( Text )event.widget;
        viewerFilter.setText( text.getText() );
        viewer.refresh();
      }
    } );
    elements = Elements.getInstance().getElements();
    viewer = new TableViewer( composite, SWT.BORDER );
    viewer.setUseHashlookup( true );
    viewer.setContentProvider( new ElementsContentProvider() );
    viewer.setLabelProvider( new ElementsLabelProvider() );
    createColumn( "Nr.", 50, NUMBER );
    createColumn( "Sym.", 50, SYMBOL );
    createColumn( "Name", 140, NAME );
    viewer.setInput( elements );
    viewer.addFilter( viewerFilter );
    viewer.addSelectionChangedListener( new ISelectionChangedListener() {

      public void selectionChanged( final SelectionChangedEvent event ) {
        StructuredSelection sel = (StructuredSelection) event.getSelection();
        Element firstElement = ( Element )sel.getFirstElement();
        if( firstElement != null ) {
          lblSelection.setText( firstElement.name
                                + " ("
                                + firstElement.symbol
                                + ")" );
        } else {
          lblSelection.setText( "" );
        }
      }
    } );
    viewer.getTable().setHeaderVisible( true );
    GridData tableData = new GridData( SWT.FILL, SWT.FILL, true, true );
    tableData.horizontalSpan = 2;
    viewer.getTable().setLayoutData( tableData );
    lblSelection = new Label( composite, SWT.NONE );
    GridData labelData = new GridData( SWT.FILL, SWT.FILL, true, true );
    labelData.minimumHeight = 50;
    labelData.horizontalSpan = 2;
    lblSelection.setLayoutData( labelData );
  }

  private TableViewerColumn createColumn( final String text,
                                          final int width,
                                          final int sortProperty )
  {
    TableViewerColumn result = new TableViewerColumn( viewer, SWT.NONE );
    result.setLabelProvider( labelProvider );
    TableColumn column = result.getColumn();
    column.setText( text );
    column.setWidth( width );
    column.setMoveable( true );
    column.addSelectionListener( new SelectionAdapter() {
      public void widgetSelected( final SelectionEvent event ) {
        int sortDirection = updateSortDirection( ( TableColumn )event.widget );
        sort( viewer, sortProperty, sortDirection == SWT.DOWN );
      }
    } );
    return result;
  }

  private static int updateSortDirection( final TableColumn column ) {
    Table table = column.getParent();
    if( column == table.getSortColumn() ) {
      if( table.getSortDirection() == SWT.UP ) {
        table.setSortDirection( SWT.DOWN );
      } else {
        table.setSortDirection( SWT.UP );
      }
    } else {
      table.setSortColumn( column );
      table.setSortDirection( SWT.DOWN );
    }
    return table.getSortDirection();
  }

  private static void sort( final TableViewer viewer,
                            final int property,
                            final boolean ascending )
  {
    if( ( viewer.getControl().getStyle() & SWT.VIRTUAL ) != 0 ) {
      List input = ( List )viewer.getInput();
      Collections.sort( input, new ElementComparator( property, ascending ) );
      viewer.refresh();
    } else {
      viewer.setComparator( new ElementComparator( property, ascending ) );
    }
  }

  private static final class ElementsContentProvider
    implements IStructuredContentProvider
  {
    Object[] elements;
    public Object[] getElements( final Object inputElement ) {
      return elements;
    }

    public void inputChanged( final Viewer viewer,
                              final Object oldInput,
                              final Object newInput )
    {
      if( newInput == null ) {
        elements = new Object[ 0 ];
      } else {
        java.util.List elementsList = ( java.util.List )newInput;
        elements = elementsList.toArray();
      }
    }

    public void dispose() {
      // do nothing
    }
  }

  private static final class ElementsLabelProvider
    extends CellLabelProvider
  {
    public void update( final ViewerCell cell ) {
      Element element = ( Element )cell.getElement();
      int columnIndex = cell.getColumnIndex();
      switch( columnIndex ) {
        case NUMBER:
          cell.setText( String.valueOf( element.number ) );
          break;
        case NAME:
          cell.setText( element.name );
          break;
        case SYMBOL:
          cell.setText( element.symbol );
          break;
      }
    }
  }

  private static final class ElementComparator
    extends ViewerComparator
    implements Comparator
  {

    private final boolean ascending;
    
    private final int property;
    
    public ElementComparator( final int property, final boolean ascending ) {
      this.property = property;
      this.ascending = ascending;
    }
    
    public int compare( final Viewer viewer,
                        final Object object1,
                        final Object object2 )
    {
      return compare( object1, object2 );
    }
    
    public boolean isSorterProperty( final Object elem, final String property )
    {
      return true;
    }
    
    public int compare( final Object object1, final Object object2 ) {
      Element element1 = ( Element )object1;
      Element element2 = ( Element )object2;
      int result = 0;
      if( property == NAME ) {
        result = element1.name.compareTo( element2.name );
      } else if( property == NUMBER ) {
        if( element1.number < element2.number ) {
          result = -1;
        } else if( element1.number > element2.number ) {
          result = +1;
        }
      } else if( property == SYMBOL ) {
        result = element1.symbol.compareTo( element2.symbol );
      }
      if( !ascending ) {
        result = result * -1;
      }
      return result;
    }
  }

  private static final class ElementsFilter extends ViewerFilter {

    private String text;

    public void setText( final String string ) {
      this.text = string;
    }

    public boolean select( final Viewer viewer,
                           final Object parentElement,
                           final Object element )
    {
      boolean result = true;
      Element chemElement = ( Element )element;
      if( text != null && text.length() > 0 ) {
        String elementText = chemElement.name.toLowerCase();
        result = elementText.indexOf( text.toLowerCase() ) != -1;
      }
      return result;
    }

    public boolean isFilterProperty( final Object element, final String prop ) {
      return true;
    }
  }
}
