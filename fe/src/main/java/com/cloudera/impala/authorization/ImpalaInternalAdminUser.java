// Copyright 2013 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.impala.authorization;

/*
 * A singleton class that represents a special user type used for internal Impala
 * sessions (for example, populating the debug webpage Catalog view). This user has
 * all privileges on all objects in the server.
 */
public class ImpalaInternalAdminUser extends User {
  private final static ImpalaInternalAdminUser instance = new ImpalaInternalAdminUser();

  private ImpalaInternalAdminUser() {
    super("Impala Internal Admin User");
  }

  /*
   * Returns an instance of the ImpalaInternalAdminUser.
   */
  public static ImpalaInternalAdminUser getInstance() {
    return instance;
  }
}