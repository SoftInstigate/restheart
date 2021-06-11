// package org.restheart.graal;

// import com.oracle.svm.core.annotate.Alias;
// import com.oracle.svm.core.annotate.RecomputeFieldValue;
// import com.oracle.svm.core.annotate.TargetClass;
// import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;


// /**
//  * Seems to be needed to upgrade coffeine
//  * More work is needed since native image build still fails
//  *
//  * ref: https://github.com/oracle/graal/issues/1572#issuecomment-525026044
//  *
//  * not working due to error
//  * Error: Substitution target for org.restheart.graal.Target_org_jctools_util_UnsafeRefArrayAccess is not loaded. Use field `onlyWith` in the `TargetClass` annotation to make substitution only active when needed.
//  *
//  * using onlyWith = com.oracle.svm.graal.hosted.GraalFeature.IsEnabled.class does NOT fix it
//  */
// //@TargetClass(className = "org.jctools.util.UnsafeRefArrayAccess")
// final class Target_org_jctools_util_UnsafeRefArrayAccess {
//     @Alias
//     @RecomputeFieldValue(kind = Kind.ArrayIndexShift, declClass = Object[].class)
//     public static int REF_ELEMENT_SHIFT;
// }