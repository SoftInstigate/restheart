/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
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
