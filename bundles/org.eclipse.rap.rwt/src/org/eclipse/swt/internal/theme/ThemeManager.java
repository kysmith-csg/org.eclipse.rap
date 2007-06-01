/*******************************************************************************
 * Copyright (c) 2007 Innoopract Informationssysteme GmbH. All rights
 * reserved. This program and the accompanying materials are made available
 * under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * Contributors: Innoopract Informationssysteme GmbH - initial API and
 * implementation
 ******************************************************************************/

package org.eclipse.swt.internal.theme;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.swt.resources.ResourceManager;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;
import org.xml.sax.SAXException;

import com.w4t.HtmlResponseWriter;
import com.w4t.engine.service.ContextProvider;
import com.w4t.engine.service.IServiceStateInfo;

public class ThemeManager {

  private static final String PREDEFINED_THEME_NAME = "RAP Default Theme";
  private static final String PREDEFINED_THEME_ID
    = "org.eclipse.swt.theme.Default";

  private static ThemeManager instance;
  
  private final Map themes;
  private final Map adapters;
  private Theme predefinedTheme;
  private boolean initialized;
  private String defaultThemeId = PREDEFINED_THEME_ID;

  // TODO [rst] Remove sysout blocks when themeing has stabilized
  private static final boolean DEBUG = false;
  
  private static final Class[] THEMEABLE_WIDGETS = new Class[] {
    Widget.class,
    Control.class,
    Button.class,
    Shell.class,
    List.class
  };
  
  private ThemeManager() {
    // prevent instantiation from outside
    themes = new HashMap();
    adapters = new HashMap();
    initialized = false;
  }
  
  /**
   * Returns the sole instance of the ThemeManager.
   */
  public static ThemeManager getInstance() {
    if( instance == null ) {
      instance = new ThemeManager();
    }
    return instance;
  }
  
  /**
   * Initializes the ThemeManager, i.e. loads themeing-relevant files and
   * registers themes.
   */
  public void initialize() {
    if( DEBUG ) {
      System.out.println( "____ ThemeManager intialize" );
    }
    if( !initialized ) {
      predefinedTheme = new Theme( PREDEFINED_THEME_NAME );
      for( int i = 0; i < THEMEABLE_WIDGETS.length; i++ ) {
        try {
          processThemeableWidget( THEMEABLE_WIDGETS[ i ] );
        } catch( Exception e ) {
          throw new RuntimeException( "Initialization failed", e );
        }
      }
      themes.put( PREDEFINED_THEME_ID, predefinedTheme );
      initialized = true;
      if( DEBUG  ) {
        System.out.println( "=== REGISTERED ADAPTERS ===" );
        Iterator iter = adapters.keySet().iterator();
        while( iter.hasNext() ) {
          Class key = ( Class )iter.next();
          Object adapter = adapters.get( key );
          System.out.println( key.getName() + ": " + adapter );        
        }
        System.out.println( "=== END REGISTERED ADAPTERS ===" );
      }
    }
  }
  
  public void deregisterAll() {
    if( initialized ) {
      themes.clear();
      adapters.clear();
      predefinedTheme = null;
      initialized = false;
      if( DEBUG ) {
        System.out.println( "deregistered" );
      }
    }
  }
  
  public void registerTheme( final String id,
                             final String name,
                             final InputStream instr,
                             final boolean asDefault )
    throws IOException
  {
    checkInitialized();
    if( DEBUG ) {
      System.out.println( "_____ register theme " + id + ": " + instr );
    }
    checkId( id );
    if( themes.containsKey( id ) ) {
      String pattern = "Theme with id ''{0}'' exists already";
      Object[] arguments = new Object[] { id };
      String msg = MessageFormat.format( pattern, arguments );
      throw new IllegalArgumentException( msg );
    }
    Theme theme = loadThemeFile( name, instr );
    themes.put( id, theme );
    if( asDefault ) {
      defaultThemeId = id;
    }
  }

  public void registerResources() throws IOException {
    checkInitialized();
    if( DEBUG ) {
      System.out.println( "____ ThemeManager register res." );
    }
    Iterator iterator = themes.keySet().iterator();
    while( iterator.hasNext() ) {
      String id = ( String )iterator.next();
      Theme theme = ( Theme )themes.get( id );
      registerThemeFiles( theme, id );
    }
  }
  
  public boolean hasTheme( final String themeId ) {
    checkInitialized();
    return themes.containsKey( themeId );
  }

  public Theme getTheme( final String themeId ) {
    checkInitialized();
    if( !hasTheme( themeId ) ) {
      throw new IllegalArgumentException( "No theme registered with id "
                                          + themeId );
    }
    return ( Theme )themes.get( themeId );
  }
  
  public String getDefaultThemeId() {
    checkInitialized();
    return defaultThemeId;
  }
  
  public boolean hasThemeAdapter( final Class controlClass ) {
    checkInitialized();
    return adapters.containsKey( controlClass );
  }

  public IThemeAdapter getThemeAdapter( final Class widgetClass ) {
    checkInitialized();
    IThemeAdapter result = null;
    Class clazz = widgetClass;
    while( clazz != null && clazz != Widget.class && !hasThemeAdapter( clazz ) ) {
      clazz = clazz.getSuperclass();
    }
    if( hasThemeAdapter( clazz ) ) {
      result = ( IThemeAdapter )adapters.get( clazz );
    } else {
      throw new IllegalArgumentException( "No theme adapter registered for class "
                                          + widgetClass.getName() );
    }
    return result;
  }

  private void checkInitialized() {
    if( !initialized ) {
      throw new IllegalStateException( "ThemeManager not initialized" );
    }
  }

  /**
   * Loads all necessary resources for a given class.
   * @throws InvalidThemeFormatException 
   * @throws SAXException 
   * @throws ParserConfigurationException 
   * @throws FactoryConfigurationError 
   */
  private void processThemeableWidget( final Class clazz )
    throws IOException, FactoryConfigurationError,
    ParserConfigurationException, SAXException
  {
    String[] variants = getPackageVariants( clazz.getPackage().getName() );
    String className = getSimpleClassName( clazz );
    ClassLoader loader = clazz.getClassLoader();
    IThemeAdapter themeAdapter = null;
    Map themeDef = null;
    for( int i = 0; i < variants.length; i++ ) {
      String pkgName = variants[ i ] + "." + className.toLowerCase() + "kit";
      if( themeDef == null ) {
        themeDef = loadThemeDef( loader, pkgName, className );
      }
      if( themeAdapter == null ) {
        themeAdapter = loadThemeAdapter( loader, pkgName, className );
      }
    }
    if( themeDef != null ) {
      Iterator iterator = themeDef.keySet().iterator();
      while( iterator.hasNext() ) {
        String key = ( String )iterator.next();
        QxType value = ( QxType )themeDef.get( key );
        if( predefinedTheme.hasKey( key ) ) {
          throw new IllegalArgumentException( "Key defined twice: " + key );
        }
        predefinedTheme.setValue( key, value );
      }
    }
    if( themeAdapter != null ) {
      adapters.put( clazz, themeAdapter );
    }
  }
  
  private Map loadThemeDef( final ClassLoader loader,
                            final String pkgName,
                            final String className )
    throws IOException, FactoryConfigurationError,
    ParserConfigurationException, SAXException
  {
    Map result = null;
    String resPkgName = pkgName.replace( '.', '/' );
    String fileName = resPkgName + "/" + className + ".theme.xml";
    InputStream inStream = loader.getResourceAsStream( fileName );
    if( inStream != null ) {
      if( DEBUG ) {
        System.out.println( "Found theme definition file: " +  fileName );
      }
      try {
        ThemeDefinitionReader reader = new ThemeDefinitionReader( inStream );
        result = reader.read();
      } finally {
        // TODO [rst] stream is automatically closed by read!
        inStream.close();
      }
    }
    return result;
  }

  /**
   * Tries to load the theme adapter for a class from a given package.
   * @return the theme adapter or <code>null</code> if not found.
   */
  private IThemeAdapter loadThemeAdapter( final ClassLoader loader,
                                          final String pkgName,
                                          final String className )
  {
    IThemeAdapter result = null;
    String adapterClassName = pkgName + '.' + className + "ThemeAdapter";
    String msg = "Failed to load theme adapter for class ";
    try {
      Class adapterClass = loader.loadClass( adapterClassName );
      result = ( IThemeAdapter )adapterClass.newInstance();
    } catch( ClassNotFoundException e ) {
      // ignore and try to load from next package name variant
    } catch( InstantiationException e ) {
      throw new RuntimeException( msg + className, e );
    } catch( IllegalAccessException e ) {
      throw new RuntimeException( msg + className, e );
    }
    return result;
  }

  private Theme loadThemeFile( final String name, final InputStream instr )
    throws IOException
  {
    if( instr == null ) {
      throw new IllegalArgumentException( "null argument" );
    }
    Theme newTheme = new Theme( name, predefinedTheme );
    Properties properties = new Properties( );
    properties.load( instr );
    Iterator iterator = properties.keySet().iterator();
    while( iterator.hasNext() ) {
      String key = ( String )iterator.next();
      QxType defValue = predefinedTheme.getValue( key );
      QxType newValue = null;
      if( defValue == null ) {
        throw new IllegalArgumentException( "Invalid key for themeing: " + key );
      }
      if( defValue instanceof QxBorder ) {
        newValue = new QxBorder( (String)properties.get( key ) );
      } else if( defValue instanceof QxBoxDimensions ) {
        newValue = new QxBoxDimensions( (String)properties.get( key ) );
      } else if( defValue instanceof QxColor ) {
        newValue = new QxColor( (String)properties.get( key ) );
      } else if( defValue instanceof QxDimension ) {
        newValue = new QxDimension( (String)properties.get( key ) );
      }
      newTheme.setValue( key, newValue );
    }
    return newTheme;
  }
  
  private static void registerThemeFiles( final Theme theme, final String id )
    throws IOException
  {
    String colorThemeCode = createColorTheme( theme, id );
    String metaThemeCode = createMetaTheme( theme, id );
    if( DEBUG ) {
      System.out.println( "-- REGISTERED THEME --" );
      System.out.println( colorThemeCode );
      System.out.println( metaThemeCode );
      System.out.println( "-- END REGISTERED THEME --" );
    }
//    String prefix = ThemeWriter.THEME_NS_PREFIX.replace( '.', '/' ) + "/";
    registerJsLibrary( colorThemeCode, id + "Colors.js" );
    registerJsLibrary( metaThemeCode, id + ".js" );
  }
  
  private static String createColorTheme( final Theme theme, final String id ) {
    ThemeWriter writer = new ThemeWriter( id,
                                          theme.getName(),
                                          ThemeWriter.COLOR );
    String[] keys = theme.getKeys();
    Arrays.sort( keys );
    for( int i = 0; i < keys.length; i++ ) {
      Object value = theme.getValue( keys[ i ] );
      if( value instanceof QxColor ) {
        QxColor color = ( QxColor )value;
        writer.writeColor( keys[ i ], color );
      }
    }
    return writer.getGeneratedCode();
  }
  
  private static String createMetaTheme( final Theme theme, final String id ) {
    ThemeWriter writer = new ThemeWriter( id, theme.getName(), ThemeWriter.META );
    writer.writeTheme( "color", id + "Colors" );
    writer.writeTheme( "border", PREDEFINED_THEME_ID + "Borders" );
    writer.writeTheme( "font", PREDEFINED_THEME_ID + "Fonts" );
    writer.writeTheme( "widget", PREDEFINED_THEME_ID + "WidgetIcons" );
    writer.writeTheme( "appearance", PREDEFINED_THEME_ID + "Appearances" );
    writer.writeTheme( "icon", PREDEFINED_THEME_ID + "Icons" );
    return writer.getGeneratedCode();
  }
  
  private static void registerJsLibrary( final String code, final String name )
    throws IOException
  {
    ByteArrayInputStream resourceInputStream;
    byte[] buffer = code.getBytes( "UTF-8" );
    resourceInputStream = new ByteArrayInputStream( buffer );
    try {
      ResourceManager.getInstance().register( name, resourceInputStream );
      IServiceStateInfo stateInfo = ContextProvider.getStateInfo();
      HtmlResponseWriter responseWriter = stateInfo.getResponseWriter();
      responseWriter.useJSLibrary( name );
    } finally {
      resourceInputStream.close();
    }
  }
  
  /**
   * Inserts the package path segment <code>internal</code> at every possible
   * position in a given package name.
   */
  private static String[] getPackageVariants( final String packageName ) {
    String[] result;
    if( packageName == null || "".equals( packageName ) ) {
      result = new String[] { "internal" };
    } else {
      String[] segments = packageName.split( "\\." );
      result = new String[ segments.length + 1 ];
      for( int i = 0; i < result.length; i++ ) {
        StringBuffer buffer = new StringBuffer();
        for( int j = 0; j < segments.length; j++ ) {
          if( j == i ) {
            buffer.append( "internal." );
          }
          buffer.append( segments[ j ] );
          if( j < segments.length - 1 ) {
            buffer.append( "." );
          }
        }
        if( i == segments.length ) {
          buffer.append( ".internal" );
        }
        result[ i ] = buffer.toString();
      }
    }
    return result;
  }
  
  /**
   * For a given full class name, this method returns the class name without
   * package prefix.
   */
  // TODO [rst] Copy of LifeCycleAdapterFactory, move to a utility class?
  private static String getSimpleClassName( final Class clazz ) {
    String className = clazz.getName();
    int idx = className.lastIndexOf( '.' );
    return className.substring( idx + 1 );
  }
  
  private void checkId( final String id ) {
    if( id == null ) {
      throw new NullPointerException( "null argument" );
    }
    if( id.length() == 0 ) {
      throw new IllegalArgumentException( "empty id" );
    }
  }
}
