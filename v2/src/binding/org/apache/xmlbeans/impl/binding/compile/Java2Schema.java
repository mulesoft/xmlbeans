/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.binding.compile;

import org.apache.xmlbeans.impl.binding.bts.*;
import org.apache.xmlbeans.impl.binding.tylar.TylarWriter;
import org.apache.xmlbeans.impl.jam_old.*;
import org.apache.xmlbeans.impl.jam_old.internal.BaseJElement;
import org.apache.xmlbeans.impl.jam_old.internal.JPropertyImpl;
import org.apache.xmlbeans.impl.common.XMLChar;
import org.w3.x2001.xmlSchema.*;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.StringWriter;


/**
 * Takes a set of Java source inputs and generates a set of XML schemas to
 * which those input should be bound, as well as a binding configuration file
 * which describes to the runtime subsystem how the un/marshalling should
 * be performed.
 *
 * @author Patrick Calahan <pcal@bea.com>
 */
public class Java2Schema extends BindingCompiler {

  // =========================================================================
  // Constants

  private static final String JAVA_URI_SCHEME      = "java:";
  private static final String JAVA_NAMESPACE_URI   = "language_builtins";
  private static final String JAVA_PACKAGE_PREFIX  = "java.";

  public static final String TAG_CT               = "xsdgen:complexType";
  public static final String TAG_CT_EXCLUDE       = TAG_CT+".exclude";
  public static final String TAG_CT_TYPENAME      = TAG_CT+".typeName";
  public static final String TAG_CT_TARGETNS      = TAG_CT+".targetNamespace";
  public static final String TAG_CT_ROOT          = TAG_CT+".rootElement";
  public static final String TAG_CT_IGNORESUPER   = TAG_CT+".ignoreSuper";

  private static final String TAG_EL               = "xsdgen:element";

  public static final String TAG_EL_NAME          = TAG_EL+".name";
  public static final String TAG_EL_NILLABLE      = TAG_EL+".nillable";
  public static final String TAG_EL_EXCLUDE       = TAG_EL+".exclude";
  public static final String TAG_EL_ASTYPE        = TAG_EL+".astype";

  public static final String TAG_AT               = "xsdgen:attribute";
  public static final String TAG_AT_NAME          = TAG_AT+".name";

  // this is the character that replaces invalid characters when creating new
  // xml names.
  private static final char SAFE_CHAR = '_';

  // =========================================================================
  // Variables

  private BindingFile mBindingFile;  // the file we're creating
  private BindingLoader mLoader; // the full loader: bindingFile + baseLoader
  private SchemaDocument mSchemaDocument; // schema doc we're generating
  //private Map mTns2Schema = new HashMap();
  private SchemaDocument.Schema mSchema;
  private JClass[] mClasses; // the input classes
  private JAnnotationLoader mAnnotationLoader = null;

  // =========================================================================
  // Constructors

  public Java2Schema(JClass[] classesToBind) {
    if (classesToBind == null) {
      throw new IllegalArgumentException("null classes");
    }
    mClasses = classesToBind;
  }

  // ========================================================================
  // Public methods

  /**
   * Sets the JAnnotionLoader to be used to 'overlay' external annotations
   * onto the input JClass set.
   */
  public void setAnnotationLoader(JAnnotationLoader jal) {
    if (jal == null) throw new IllegalArgumentException("null jal");
    mAnnotationLoader = jal;
    //FIXME this is a gross quick hack to get the external annotations
    //working.  long term, we need to extend jam_old to allow a jam_old facade to be
    //created that imposes the annotations without actually modifying the
    //input JClasses like we do here.
    for(int i=0; i<mClasses.length; i++) {
      ((BaseJElement)mClasses[i]).setAnnotationLoader(mAnnotationLoader);
    }
  }

  // ========================================================================
  // BindingCompiler implementation

  /**
   * Does the binding work on the inputs passed to the constructor and writes
   * out the tylar.
   */
  protected void internalBind(TylarWriter writer) {
    mBindingFile = new BindingFile();
    mLoader = CompositeBindingLoader.forPath
            (new BindingLoader[] {mBindingFile, super.getBaseBindingLoader()});
    mSchemaDocument = SchemaDocument.Factory.newInstance();
    mSchema = mSchemaDocument.addNewSchema();
    if (mClasses.length > 0) {
      //FIXME how should we determine the targetnamespace for the schema?
      //here we just derive it from the first class in the list
      mSchema.setTargetNamespace(getTargetNamespace(mClasses[0]));
    }
    //This does the binding
    for(int i=0; i<mClasses.length; i++) {
      if (!getAnnotation(mClasses[i],TAG_CT_EXCLUDE,false)) {
        getBindingTypeFor(mClasses[i]);
      }
    }
    //
    try {
      writer.writeBindingFile(mBindingFile);
      writer.writeSchema(mSchemaDocument,"schema-0.xsd");
    } catch(IOException ioe) {
      logError(ioe);
    }
  }

  // ========================================================================
  // Private methods

  /**
   * Returns a bts BindingType for the given JClass.  If such a type
   * has not yet been registered with the loader, it will be created.
   *
   * @param clazz Java type for which to return a binding.
   */
  private BindingType getBindingTypeFor(JClass clazz) {
    BindingTypeName btn = mLoader.lookupTypeFor(getJavaName(clazz));
    if (btn != null) {
      BindingType out = mLoader.getBindingType(btn);
      if (out != null) return out;
    }
    return createBindingTypeFor(clazz);
  }

  /**
   * Creates a bts BindingType for the given JClass and registers t with the
   * loader.  Note that this method assumes that a BindingType does not
   * already exist for the given JClass.
   *
   * @param clazz Java type for which to generate a binding.
   */
  private BindingType createBindingTypeFor(JClass clazz) {
    // create the schema type
    TopLevelComplexType xsType = mSchema.addNewComplexType();
    String tns = getTargetNamespace(clazz);
    String xsdName = getAnnotation(clazz,TAG_CT_TYPENAME,clazz.getSimpleName());
    QName qname = new QName(tns,xsdName);
    xsType.setName(xsdName);
    // deal with inheritance - see if it extends anything
    JClass superclass = clazz.getSuperclass();
    if (superclass != null && !superclass.isObject() &&
            !getAnnotation(clazz,TAG_CT_IGNORESUPER,false)) {
      // FIXME we're ignoring interfaces at the moment
      BindingType superBindingType = getBindingTypeFor(superclass);
      ComplexContentDocument.ComplexContent ccd = xsType.addNewComplexContent();
      ExtensionType et = ccd.addNewExtension();
      et.setBase(superBindingType.getName().getXmlName().getQName());
    }
    // create a binding type
    BindingTypeName btname = BindingTypeName.forPair(getJavaName(clazz),
                                                     XmlTypeName.forTypeNamed(qname));
    ByNameBean bindType = new ByNameBean(btname);
    mBindingFile.addBindingType(bindType,true,true);
    if (clazz.isPrimitive()) {
      // it's good to have registerd the dummy type, but don't go further
      logError("Unexpected simple type",clazz);
      return bindType;
    }
    String rootName = getAnnotation(clazz,TAG_CT_ROOT,null);
    if (rootName != null) {
      QName rootQName = new QName(tns, rootName);
      BindingTypeName docBtName =
              BindingTypeName.forPair(getJavaName(clazz),
                                      XmlTypeName.forGlobalName(XmlTypeName.ELEMENT, rootQName));
      SimpleDocumentBinding sdb = new SimpleDocumentBinding(docBtName);
      sdb.setTypeOfElement(btname.getXmlName());
      mBindingFile.addBindingType(sdb,true,true);
    }
    // run through the class' properties to populate the binding and xsdtypes
    SchemaPropertyFacade facade = new SchemaPropertyFacade(xsType,bindType,tns);
    bindProperties(JPropertyImpl.getProperties(clazz.getDeclaredMethods()),facade);
    facade.finish();
    // check to see if they want to create a root elements from this type
    JAnnotation[] anns = clazz.getAnnotations(TAG_CT_ROOT);
    for(int i=0; i<anns.length; i++) {
      TopLevelElement root = mSchema.addNewElement();
      root.setName(makeNcNameSafe(anns[i].getStringValue()));
      root.setType(qname);
      // FIXME still not entirely clear to me what we should do about
      // the binding file here
    }
    return bindType;
  }

  /**
   * Runs through a set of JProperties to creates schema and bts elements
   * to represent those properties.  Note that the details of manipulating the
   * schema and bts are encapsulated within the supplied SchemaPropertyFacade;
   * this method is only responsible for inspecting the properties and their
   * annotations and setting the correct attributes on the facade.
   *
   * @param props Array of JProperty objects to potentially be bound.
   * @param facade Allows us to create and manipulate properties,
   * hides the dirty work
   */
  private void bindProperties(JProperty[] props, SchemaPropertyFacade facade) {
    for(int i=0; i<props.length; i++) {
      if (getAnnotation(props[i],TAG_EL_EXCLUDE,false)) {
        logVerbose("Marked excluded, skipping",props[i]);
        continue;
      }
      if (props[i].getGetter() == null || props[i].getSetter() == null) {
        logVerbose("Does not have both getter and setter, skipping",props[i]);
        continue; // REVIEW this might have to change someday
      }
      { // determine the property name to use and set it
        String propName = getAnnotation(props[i],TAG_AT_NAME,null);
        if (propName != null) {
          facade.newAttributeProperty(props[i]);
          facade.setSchemaName(propName);
        } else {
          facade.newElementProperty(props[i]);
          facade.setSchemaName(getAnnotation
                         (props[i],TAG_EL_NAME,props[i].getSimpleName()));
        }
      }
      { // determine the property type to use and set it
        JClass propType = null;
        String annotatedType = getAnnotation(props[i],TAG_EL_ASTYPE,null);
        if (annotatedType == null) {
          facade.setType(propType = props[i].getType());
        } else {
          if (props[i].getType().isArray()) {
            //THIS IS A QUICK GROSS HACK THAT SHOULD BE REMOVED.
            //IF SOMEONE WANTS TO AS TYPE AN ARRAY PROPERTY, THEY NEED
            //TO ASTYPE IT TO THE ARRAY TYPE THEMSELVES
            annotatedType = "[L"+annotatedType+";";
          }
          propType = props[i].getType().forName(annotatedType);
          if (propType.isUnresolved()) {
            logError("Could not find class named '"+
                    propType.getQualifiedName()+"'",props[i]);
          } else {
            facade.setType(propType);
          }
        }
      }
      { // set the getters and setters
        facade.setGetter(props[i].getGetter());
        facade.setSetter(props[i].getSetter());
      }
      { // determine if the property is nillable
        JAnnotation a = props[i].getAnnotation(TAG_EL_NILLABLE);
        if (a != null) {
          // if the tag is there but empty, set it to true.  is that weird?
          if (a.getStringValue().trim().length() == 0) {
            facade.setNillable(true);
          } else {
            facade.setNillable(a.getBooleanValue());
          }
        }
      }
    }
  }


  /**
   * Returns a JavaTypeName for the given JClass.  Might want to pool these.
   */
  private JavaTypeName getJavaName(JClass jc) {
    return JavaTypeName.forString(jc.getQualifiedName());
  }

  /**
   * Returns the string value of a named annotation, or the provided default
   * if the annotation is not present.
   * REVIEW seems like having this functionality in jam_old would be nice
   */
  private String getAnnotation(JElement elem, String annName, String dflt) {
    JAnnotation ann = elem.getAnnotation(annName);
    return (ann == null) ? dflt : ann.getStringValue();
  }

  /**
   * Returns the boolean value of a named annotation, or the provided default
   * if the annotation is not present.
   * REVIEW seems like having this functionality in jam_old would be nice
   */
  private boolean getAnnotation(JElement elem, String annName, boolean dflt) {
    JAnnotation ann = elem.getAnnotation(annName);
//    return (ann == null) ? dflt : ann.getBooleanValue();
    if (ann == null) return false;
    String a = ann.getStringValue();
    if (a == null || a.trim().length() == 0) return true; //ewww
    return ann.getBooleanValue();
  }

  /**
   * Returns a QName for the type bound to the given JClass.
   */
  private QName getQnameFor(JClass clazz) {
    getBindingTypeFor(clazz);  //ensure that we've bound it
    JavaTypeName jtn = JavaTypeName.forString(clazz.getQualifiedName());
    BindingTypeName btn = mLoader.lookupTypeFor(jtn);
    logVerbose("BindingTypeName is "+btn,clazz);
    BindingType bt = mLoader.getBindingType(btn);
    if (bt != null) return bt.getName().getXmlName().getQName();
    logError("could not get qname",clazz);
    return new QName("ERROR",clazz.getQualifiedName());
  }

  /**
   * Returns a target namespace that should be used for the given class.
   * This takes annotations into consideration.
   */
  private String getTargetNamespace(JClass clazz) {
    JAnnotation ann = clazz.getAnnotation(TAG_CT_TARGETNS);
    if (ann != null) return ann.getStringValue();
    // Ok, they didn't specify it in the markup, so we have to
    // synthesize it from the classname.
    String pkg_name;
    if (clazz.isPrimitive()) {
      pkg_name = JAVA_NAMESPACE_URI;
    } else {
      JPackage pkg = clazz.getContainingPackage();
      pkg_name = (pkg == null) ? "" : pkg.getQualifiedName();
      if (pkg_name.startsWith(JAVA_PACKAGE_PREFIX)) {
        pkg_name = JAVA_NAMESPACE_URI+"."+
                pkg_name.substring(JAVA_PACKAGE_PREFIX.length());
      }
    }
    return JAVA_URI_SCHEME + pkg_name;
  }



  /**
   * Checks the given XML name to ensure that it is a valid XMLName according
   * to the XML 1.0 Recommendation.  If it is not, the name is mangled so
   * as to make it a valid name.  This should be called before setting the
   * name on every schema fragment we create.
   */
  private static String makeNcNameSafe(String name) {
    // it's probably pretty rare that a name isn't valid, so let's just do
    // an optimistic check first without writing out a new string.
    if (name == null || XMLChar.isValidNCName(name) || name.length() == 0) {
      return name;
    }
    // ok, we have to mangle it
    StringWriter out = new StringWriter();
    char ch = name.charAt(0);
    if(!XMLChar.isNCNameStart(ch)) {
      out.write(SAFE_CHAR);
    } else {
      out.write(ch);
    }
    for (int i=1; i < name.length(); i++ ) {
      ch = name.charAt(i);
       if (!XMLChar.isNCName(ch)) {
         out.write(ch);
       }
    }
    return out.toString();
  }

  /*

  private static boolean isXmlObj(JClass clazz) {
    try {
      JClass xmlObj = clazz.forName("org.apache.xmlbeans.XmlObject");
      return xmlObj.isAssignableFrom(clazz);
    } catch(Exception e) {
      e.printStackTrace(); //FIXME
      return false;
    }
  }
  */



  /**
   * Inner class which encapsulates the creation of schema properties and
   * property bindings and presents them as a unified interface, a kind of
   * 'virtual property.'  This is used by the bindProperties() method, and
   * allows that function to concentrate on inspecting the java types and
   * annotations. This class hides all of the dirty work associated with
   * constructing and initializing a BTS property and either an XSD element
   * or attribute.
   *
   * Note that in some sense, this class behaves as both a factory and a kind
   * of cursor.  It is capable of creating a new virtual property
   * on a given BTS/XSD type pair, and any operations on the facade will
   * apply to that property until the next property is created
   * (via newAttributeProperty or newElementProperty).
   */
  class SchemaPropertyFacade {

    // =======================================================================
    // Variables

    private TopLevelComplexType mXsType;
    private String mXsTargetNamespace;
    private LocalElement mXsElement = null; // exactly one of these two is
    private Attribute mXsAttribute = null;  // remains null
    private Group mXsSequence = null;
    private List mXsAttributeList = null;
    private ByNameBean mBtsType;
    private QNameProperty mBtsProp = null;
    private JElement mSrcContext = null;

    // =======================================================================
    // Constructors

    public SchemaPropertyFacade(TopLevelComplexType xsType,
                                ByNameBean bt,
                                String tns) {
      if (xsType == null) throw new IllegalArgumentException("null xsType");
      if (bt == null) throw new IllegalArgumentException("null bt");
      if (tns == null) throw new IllegalArgumentException("null tns");
      mXsType = xsType;
      mBtsType = bt;
      mXsTargetNamespace = tns;
    }

    // =======================================================================
    // Public methods

    /**
     * Creates a new element property and sets this facade represent it.
     * Note that either this method or newAttributeProperty must be called prior
     * to doing any work with the facade.  Also note that you need to
     * completely finish working with each property before moving onto
     * the next via newElementProperty or newAttributeProperty.
     * *
     * @param srcContext A JAM element that represents the java source
     * artifact that is being bound to the property.  This is used
     * only for error reporting purposes.
     */
    public void newElementProperty(JElement srcContext) {
      newBtsProperty();
      mSrcContext = srcContext;
      if (mXsSequence == null) mXsSequence = mXsType.addNewSequence();
      mXsElement = mXsSequence.addNewElement();
      mXsAttribute = null;
    }

    /**
     * Creates a new attribute property and sets this facade represent it.
     * Note that either this method or newElementProperty must be called prior
     * to doing any work with the facade.  Also note that you need to
     * completely finish working with each property before moving onto
     * the next via newElementProperty or newAttributeProperty.
     *
     * @param srcContext A JAM element that represents the java source
     * artifact that is being bound to the property.  This is used
     * only for error reporting purposes.
     */
    public void newAttributeProperty(JElement srcContext) {
      newBtsProperty();
      mBtsProp.setAttribute(true);
      mSrcContext = srcContext;
      mXsElement = null;
      if (mXsAttributeList == null) mXsAttributeList = new ArrayList();
      mXsAttributeList.add(mXsAttribute = Attribute.Factory.newInstance());
    }

    /**
     * Sets the name of this property (element or attribute) in the
     * generated schema.
     */
    public void setSchemaName(String name) {
      name = makeNcNameSafe(name);
      if (mXsElement != null) {
        mXsElement.setName(name);
      } else if (mXsAttribute != null) {
        mXsAttribute.setName(name);
      } else {
        throw new IllegalStateException();
      }
      mBtsProp.setQName(new QName(mXsTargetNamespace,name));
    }

    /**
     * Sets the name of the java getter for this property.
     */
    public void setGetter(JMethod g) {
      mBtsProp.setGetterName(MethodName.create(g));
    }

    /**
     * Sets the name of the java setter for this property.
     */
    public void setSetter(JMethod s) {
      mBtsProp.setSetterName(MethodName.create(s));
    }

    /**
     * Sets the type of the property.  Currently handles arrays
     * correctly but not collections.
     */
    public void setType(JClass propType) {
      if (mXsElement != null) {
        if (propType.isArray()) {
          if (propType.getArrayDimensions() != 1) {
            logError("Multidimensional arrays NYI",mSrcContext); //FIXME
          }
          JClass componentType = propType.getArrayComponentType();
          mXsElement.setMaxOccurs("unbounded");
          mXsElement.setType(getQnameFor(componentType));
          mBtsProp.setMultiple(true);
          mBtsProp.setCollectionClass //FIXME
                  (JavaTypeName.forString(componentType.getQualifiedName()+"[]"));
          mBtsProp.setBindingType(getBindingTypeFor(componentType));
        } else {
          mXsElement.setType(getQnameFor(propType));
          mBtsProp.setBindingType(getBindingTypeFor(propType));
        }
      } else if (mXsAttribute != null) {
        if (propType.isArray()) {
          logError("Array properties cannot be mapped to xml attributes",
                  mSrcContext);
        } else {
          mXsAttribute.setType(getQnameFor(propType));
          mBtsProp.setBindingType(getBindingTypeFor(propType));
        }
      } else {
        throw new IllegalStateException();
      }
    }

    /**
     * Sets whether the property should be bound as nillable.
     */
    public void setNillable(boolean b) {
      if (mXsElement != null) {
        mXsElement.setNillable(b);
        mBtsProp.setNillable(b);
      } else if (mXsAttribute != null) {
        logError("Attributes cannot be nillable:",mSrcContext);
      } else {
        throw new IllegalStateException();
      }
    }

    /**
     * This method should always be called when finished building up
     * a type.  It is a hack around an xbeans bug in which the sequences and
     * attributes are output in the order in which they were added (the
     * schema for schemas says the attributes always have to go last).
     */
    public void finish() {
      addBtsProperty();
      if (mXsAttributeList != null) {
        Attribute[] array = new Attribute[mXsAttributeList.size()];
        mXsAttributeList.toArray(array);
        mXsType.setAttributeArray(array);
      }
    }

    // =======================================================================
    // Private methods

    /**
     * Adds the current bts property to the bts type.  This has to be called
     * for every property.  We do this last because ByNameBean won't
     * let us add more than one prop for same name (name is always blank
     * initially).
     */
    private void addBtsProperty() {
      if (mBtsProp != null) mBtsType.addProperty(mBtsProp);
    }

    /**
     * Initialize a new QName property in the bts type
     */
    private void newBtsProperty() {
      if (mBtsProp != null) addBtsProperty(); //if not 1st one, add old one
      mBtsProp = new QNameProperty();
    }
  }


}