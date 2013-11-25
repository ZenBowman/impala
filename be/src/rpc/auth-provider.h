// Copyright 2012 Cloudera Inc.
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

#ifndef IMPALA_RPC_AUTH_PROVIDER_H
#define IMPALA_RPC_AUTH_PROVIDER_H

#include <string>
#include <boost/scoped_ptr.hpp>
#include <boost/thread/mutex.hpp>

#include "common/status.h"

namespace sasl { class TSasl; }

namespace impala {

class Thread;

// An AuthProvider creates Thrift transports that are set up to authenticate themselves
// using a protocol such as Kerberos or PLAIN/SASL. Both server and client transports are
// provided by this class, using slightly different mechanisms (servers use a factory,
// clients wrap a pre-provided transport)
class AuthProvider {
 public:
  // Initialises any state required to perform authentication using this provider.
  virtual Status Start() = 0;

  // Creates a new Thrift transport factory in the out parameter that performs
  // authorisation per this provider's protocol.
  virtual Status GetServerTransportFactory(
      boost::shared_ptr<apache::thrift::transport::TTransportFactory>* factory) = 0;

  // Called by Thrift clients to wrap a raw transport with any intermediate transport that
  // an auth protocol requires.
  virtual Status WrapClientTransport(const std::string& hostname,
      boost::shared_ptr<apache::thrift::transport::TTransport> raw_transport,
      boost::shared_ptr<apache::thrift::transport::TTransport>* wrapped_transport) = 0;

  // Returns true if this provider uses SASL at the transport layer.
  virtual bool is_sasl() = 0;
};

// Implements Kerberos-based authentication.
class KerberosAuthProvider : public AuthProvider {
 public:
  // FOR NOW: keytab_path must be the same for all auth provider invocations.
  KerberosAuthProvider(const std::string& principal, const std::string& keytab_path);

  // Runs Kinit in a separate thread.
  virtual Status Start();

  virtual Status WrapClientTransport(const std::string& hostname,
      boost::shared_ptr<apache::thrift::transport::TTransport> raw_transport,
      boost::shared_ptr<apache::thrift::transport::TTransport>* wrapped_transport);

  virtual Status GetServerTransportFactory(
      boost::shared_ptr<apache::thrift::transport::TTransportFactory>* factory);

  virtual bool is_sasl() { return true; }

 private:
  // Protects kinit_thread_
  static boost::mutex kinit_lock_;

  // Only initialised once. Runs Kinit periodically
  static boost::scoped_ptr<Thread> kinit_thread_;

  // The Kerberos principal to kinit as. Set from FLAGS_principal usually.
  std::string principal_;

  // The full path to the system keytab
  std::string keytab_path_;

  // The service name, deduced from the principal. Used only by clients.
  std::string service_name_;

  // Runs kinit periodically to maintain a live ticket for principal_.
  void RunKinit();
};

// Provider for Ldap-based authentication. In fact, this only sets up PLAIN/SASL as the
// client<->server mechanism. External configuration is required via the usual
// <appname>.conf file in the system SASL configuration directory to set up LDAP
// authentication/
//
// This provider should only be used with servers that use SSL, since passwords are sent
// in plain text over the wire.
class LdapAuthProvider : public AuthProvider {
 public:
  LdapAuthProvider() { }
  virtual Status Start() { return Status::OK; }
  virtual Status GetServerTransportFactory(
      boost::shared_ptr<apache::thrift::transport::TTransportFactory>* factory);
  virtual Status WrapClientTransport(const std::string& hostname,
      boost::shared_ptr<apache::thrift::transport::TTransport> raw_transport,
      boost::shared_ptr<apache::thrift::transport::TTransport>* wrapped_transport);

  virtual bool is_sasl() { return true; }
};

// This provider implements no authentication, so any connection is immediately
// successful.
class NoAuthProvider : public AuthProvider {
 public:
  NoAuthProvider() { }

  virtual Status Start() { return Status::OK; }

  virtual Status GetServerTransportFactory(
      boost::shared_ptr<apache::thrift::transport::TTransportFactory>* factory);

  virtual Status WrapClientTransport(const std::string& hostname,
      boost::shared_ptr<apache::thrift::transport::TTransport> raw_transport,
      boost::shared_ptr<apache::thrift::transport::TTransport>* wrapped_transport);

  virtual bool is_sasl() { return false; }
};

// Initialises the authentication subsystem, including SASL and the global AuthManager
// instance.
Status InitAuth(const std::string& appname);

}

#endif