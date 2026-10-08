/**
 * DataProvider - Mock now; RpcDataProvider later without rewriting pages.
 */
(function (global) {
  function MockDataProvider() {
    this.kind = "mock";
  }
  MockDataProvider.prototype.list = function (collection, query) {
    return Promise.resolve(JnMockData.list(collection, query));
  };
  MockDataProvider.prototype.get = function (collection, id) {
    return Promise.resolve(JnMockData.get(collection, id));
  };
  MockDataProvider.prototype.save = function (collection, record) {
    return Promise.resolve(JnMockData.save(collection, record));
  };
  MockDataProvider.prototype.remove = function (collection, id) {
    return Promise.resolve(JnMockData.remove(collection, id));
  };
  MockDataProvider.prototype.dashboard = function (kind) {
    return Promise.resolve(JnMockData.dashboard(kind));
  };

  function RpcDataProvider(baseUrl) {
    this.kind = "rpc";
    this.baseUrl = baseUrl || "";
  }
  RpcDataProvider.prototype.list = function () {
    return Promise.reject(new Error("RpcDataProvider not connected in UI Foundation 1.0 DEMO mode"));
  };
  RpcDataProvider.prototype.get = function () {
    return Promise.reject(new Error("RpcDataProvider not connected in UI Foundation 1.0 DEMO mode"));
  };
  RpcDataProvider.prototype.save = function () {
    return Promise.reject(new Error("RpcDataProvider not connected in UI Foundation 1.0 DEMO mode"));
  };
  RpcDataProvider.prototype.remove = function () {
    return Promise.reject(new Error("RpcDataProvider not connected in UI Foundation 1.0 DEMO mode"));
  };
  RpcDataProvider.prototype.dashboard = function () {
    return Promise.reject(new Error("RpcDataProvider not connected in UI Foundation 1.0 DEMO mode"));
  };

  global.JnData = {
    MockDataProvider: MockDataProvider,
    RpcDataProvider: RpcDataProvider,
    active: new MockDataProvider(),
    useMock: function () { this.active = new MockDataProvider(); },
    useRpc: function (url) { this.active = new RpcDataProvider(url); }
  };
})(window);
