/** WIP
 * Automate reflect configuratio currently done via GenerateGraalvmReflectConfig
 *
 */

// package org.restheart.graal;

// import com.oracle.svm.core.annotate.AutomaticFeature;

// import org.graalvm.nativeimage.hosted.Feature;
// import org.graalvm.nativeimage.hosted.RuntimeReflection;

//@AutomaticFeature
/** WIP automate reflect conf currently done via GenerateGraalvmReflectConfig
 * see https://www.graalvm.org/reference-manual/native-image/Reflection/
 */
// public class RuntimeReflectionRegistrationFeature implements Feature {
//   public void beforeAnalysis(BeforeAnalysisAccess access) {
//     try {
//       RuntimeReflection.register(String.class);
//       RuntimeReflection.register(String.class.getDeclaredField("value"));
//       RuntimeReflection.register(String.class.getDeclaredField("hash"));
//       RuntimeReflection.register(String.class.getDeclaredConstructor(char[].class));
//       RuntimeReflection.register(String.class.getDeclaredMethod("charAt", int.class));
//       RuntimeReflection.register(String.class.getDeclaredMethod("format", String.class, Object[].class));
//       RuntimeReflection.register(String.CaseInsensitiveComparator.class);
//       RuntimeReflection.register(String.CaseInsensitiveComparator.class.getDeclaredMethod("compare", String.class, String.class));
//     } catch (NoSuchMethodException | NoSuchFieldException e) { 
//         // nothing to do
//     }
//   }
// }