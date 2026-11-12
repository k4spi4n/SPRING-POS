const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

// A small DOM double tests event handlers and HTTP contracts without adding a browser dependency.
class Node {
  constructor(tag = "div") {
    this.tag = tag; this.children = []; this.listeners = {}; this.dataset = {};
    this.value = ""; this.textContent = ""; this.disabled = false; this.hidden = false;
    this.classList = { toggle() {} };
  }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  setAttribute(name, value) { this[name] = value; }
  setCustomValidity(text) { this.validation = text; }
  set value(value) { this.storedValue = String(value); }
  get value() { return this.tag === "select" && !this.children.some(node => node.value === this.storedValue) ? "" : this.storedValue; }
  get valueAsNumber() { return this.value === "" ? NaN : Number(this.value); }
  reportValidity() { return !this.validation; }
  focus() { this.focused = true; }
  reset() {}
  addEventListener(name, listener) { (this.listeners[name] ||= []).push(listener); }
  async fire(name) { for (const listener of this.listeners[name] || []) await listener({ preventDefault() {} }); }
  dispatchEvent(event) { for (const listener of this.listeners[event.type] || []) listener(event); }
  querySelectorAll(selector) { return this.children.flatMap(node => [...((selector.startsWith(".") ? node.className?.split(" ").includes(selector.slice(1)) : node.tag === selector) ? [node] : []), ...node.querySelectorAll(selector)]); }
  querySelector(selector) { return this.querySelectorAll(selector)[0] || null; }
  showModal() { this.open = true; }
  close() { this.open = false; this.dispatchEvent({ type: "close" }); }
}

function setup() {
  const nodes = new Map();
  const get = id => {
    for (const root of nodes.values()) {
      const found = root.querySelectorAll("input").concat(root.querySelectorAll("p")).find(node => node.id === id);
      if (found) return found;
    }
    if (!nodes.has(id)) nodes.set(id, Object.assign(new Node(["warehouse", "category", "sort"].includes(id) ? "select" : "div"), { id }));
    return nodes.get(id);
  };
  const calls = [];
  const product = { id: 7, name: "Sữa thật từ API", sku: "MILK", unit: "Hộp", sellingPrice: 12.5, active: true, categoryName: "Sữa" };
  const replies = new Map([
    ["/auth/login", { token: "test-token", fullName: "Nhân viên", role: "ROLE_STAFF" }],
    ["/products", { content: [product], pageNo: 0, totalPages: 1, totalElements: 1 }],
    ["/warehouses/active", [{ id: 1, code: "WH", name: "Kho" }]],
    ["/categories", [{ id: 2, name: "Sữa" }]],
    ["/auth/logout", null],
    ["/issues", { issueCode: "PXK-123", status: "COMPLETED", warehouseName: "Kho", destination: "Cửa hàng", totalAmount: 42.75, items: [{ productName: product.name, quantity: 3, unit: "Hộp", unitPrice: 14.25, subTotal: 42.75, batchNumber: "LOT", expiryDate: "2027-01-01" }] }]
  ]);
  const reply = (data, status = 200, extra = {}) => ({ ok: status < 400, status, json: async () => ({ success: status < 400, data, ...extra }) });
  const context = vm.createContext({
    document: { getElementById: get, createElement: tag => new Node(tag) },
    Option: function(text, value) { const node = new Node("option"); node.textContent = text; node.value = String(value); return node; },
    Intl, URLSearchParams, AbortSignal, Event,
    fetch: async (url, options) => {
      const endpoint = url.replace("api/v1", "").split("?")[0];
      calls.push({ endpoint, url, options, body: options.body && JSON.parse(options.body) });
      const value = replies.get(endpoint);
      return typeof value === "function" ? value() : reply(value);
    }
  });
  vm.runInContext(fs.readFileSync(path.join(__dirname, "../../main/resources/static/pos.js"), "utf8"), context);
  get("sort").append(new context.Option("A-Z", "name,asc"));
  get("sort").value = "name,asc";
  const read = code => vm.runInContext(code, context);
  const login = async () => { get("username").value = "staff"; get("password").value = "test-only"; await get("login-form").fire("submit"); };
  const add = () => get("products").querySelectorAll("button")[0].fire("click");
  const ready = async () => { await login(); get("warehouse").value = "1"; get("destination").value = "Cửa hàng"; await get("warehouse").fire("change"); };
  return { get, calls, product, replies, reply, read, login, ready, add };
}

test("login, add before destination, edit, remove, search and create with server-confirmed amount", async () => {
  const app = setup();
  const { get, read, calls } = app;
  await app.login();
  assert.equal(get("password").value, "");
  assert.equal(get("workspace").hidden, false);
  await app.add();
  assert.equal(read("cart.size"), 1);
  let quantity = get("cart").querySelectorAll("input")[0];
  quantity.value = "3"; await quantity.fire("input");
  assert.equal(get("total").textContent, new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 2 }).format(37.5));
  await get("cart").querySelectorAll("button").find(button => button["aria-label"]?.startsWith("Xóa ")).fire("click");
  assert.equal(read("cart.size"), 0);
  await app.add();
  quantity = get("cart").querySelectorAll("input")[0];
  quantity.value = "3"; await quantity.fire("input");
  get("search").value = "milk & tea";
  await get("search-form").fire("submit");
  assert.match(calls.at(-1).url, /search=milk\+%26\+tea/);
  get("warehouse").value = "1"; get("destination").value = " Cửa hàng ";
  await get("order-form").fire("submit");
  const request = calls.find(call => call.endpoint === "/issues");
  assert.deepEqual(request.body, { warehouseId: 1, destination: "Cửa hàng", issueType: "STORE_TRANSFER", note: "", items: [{ productId: 7, quantity: 3 }] });
  assert.equal(request.options.headers.Authorization, "Bearer test-token");
  assert.equal(read("cart.size"), 0);
  assert.equal(get("result").hidden, false);
  assert.match(get("result-content").children[2].textContent, /42,75/);
});

test("blocks duplicate submission and cart changes while awaiting response; preserves cart on stock rejection", async () => {
  const app = setup(); await app.ready(); await app.add();
  let finish;
  app.replies.set("/issues", () => new Promise(resolve => { finish = resolve; }));
  const pending = app.get("order-form").fire("submit");
  assert.equal(app.get("order-fields").disabled, true);
  assert.equal(app.get("logout").disabled, true);
  await app.get("order-form").fire("submit"); await app.add();
  assert.equal(app.calls.filter(call => call.endpoint === "/issues").length, 1);
  finish(app.reply(null, 400, { errorCode: "INSUFFICIENT_STOCK", message: "Không đủ tồn kho" }));
  await pending;
  assert.equal(app.read("cart.get(7).quantity"), 1);
  assert.match(app.get("order-message").textContent, /Không đủ tồn kho/);
  assert.equal(app.get("submit-order").disabled, false);
});

test("invalid quantity and whitespace destination cannot submit", async () => {
  const app = setup(); await app.ready(); await app.add();
  const quantity = app.get("cart").querySelectorAll("input")[0];
  app.get("order-form").reportValidity = () => quantity.reportValidity();
  for (const value of ["", "0", "-1", "1.5", "2147483648"]) {
    quantity.value = value; await quantity.fire("input");
    await app.get("order-form").fire("submit");
    assert.ok(quantity.validation);
  }
  quantity.value = "2"; await quantity.fire("input");
  app.get("destination").value = "   ";
  await app.get("order-form").fire("submit");
  assert.equal(app.calls.filter(call => call.endpoint === "/issues").length, 0);
  assert.match(app.get("order-message").textContent, /không được để trống/);
});

test("displays backend validation details and request id", async () => {
  const app = setup(); await app.ready(); await app.add();
  app.replies.set("/issues", () => app.reply({ destination: "Nơi nhận không hợp lệ" }, 400, { errorCode: "VALIDATION_ERROR", message: "Dữ liệu không hợp lệ", requestId: "trace-42" }));
  await app.get("order-form").fire("submit");
  assert.match(app.get("order-message").textContent, /Nơi nhận không hợp lệ/);
  assert.match(app.get("order-message").textContent, /trace-42/);
  assert.equal(app.read("cart.size"), 1);
});

test("network failure shows uncertainty, keeps cart and allows recovery", async () => {
  const app = setup(); await app.ready(); await app.add();
  app.replies.set("/issues", () => { throw new TypeError("Offline"); });
  await app.get("order-form").fire("submit");
  assert.match(app.get("order-message").textContent, /đơn có thể đã được tạo/);
  assert.equal(app.read("cart.size"), 1);
  assert.equal(app.get("submit-order").disabled, false);
  app.replies.set("/products", () => { throw new TypeError("Offline"); });
  await app.read("loadProducts()");
  assert.match(app.get("catalog-message").textContent, /kiểm tra kết nối/);
  assert.equal(app.get("catalog-message").hidden, false);
});

test("401 requests reauthentication and keeps draft; empty product and warehouse lists are explicit", async () => {
  const app = setup(); await app.ready(); await app.add();
  app.replies.set("/issues", () => app.reply(null, 401, { message: "Token hết hạn" }));
  await app.get("order-form").fire("submit");
  assert.equal(app.get("login-panel").hidden, false);
  assert.equal(app.get("workspace").hidden, true);
  assert.equal(app.read("cart.size"), 1);
  assert.match(app.get("login-message").textContent, /giỏ hàng vẫn được giữ/);
  app.replies.set("/products", { content: [], pageNo: 0, totalPages: 0, totalElements: 0 });
  app.replies.set("/warehouses/active", []);
  await app.login();
  assert.match(app.get("catalog-message").textContent, /Không có sản phẩm/);
  assert.match(app.get("warehouse-message").textContent, /Chưa có kho/);
  assert.equal(app.get("submit-order").disabled, true);
});

test("out-of-order search responses cannot replace the latest result", async () => {
  const app = setup(); await app.ready();
  let finish;
  app.replies.set("/products", () => new Promise(resolve => { finish = resolve; }));
  const first = app.read("loadProducts()");
  app.replies.set("/products", { content: [], pageNo: 0, totalPages: 0, totalElements: 0 });
  await app.read("loadProducts()");
  finish(app.reply({ content: [app.product], pageNo: 0, totalPages: 1, totalElements: 1 }));
  await first;
  assert.equal(app.get("products").children.length, 0);
  assert.match(app.get("catalog-message").textContent, /Không có sản phẩm/);
});

test("category filtering and price sorting request the backend and reset pagination", async () => {
  const app = setup(); await app.ready();
  app.read("page = 4");
  app.get("category").value = "2";
  await app.get("category").fire("change");
  const filtered = app.calls.at(-1);
  assert.match(filtered.url, /categoryId=2/);
  assert.match(filtered.url, /page=0/);
  app.get("sort").append({ tag: "option", value: "sellingPrice,asc", querySelectorAll() { return []; } });
  app.get("sort").value = "sellingPrice,asc";
  await app.get("sort").fire("change");
  assert.match(app.calls.at(-1).url, /sort=sellingPrice%2Casc/);
  await app.get("reset-search").fire("click");
  assert.doesNotMatch(app.calls.at(-1).url, /categoryId=/);
  assert.equal(app.read("cart.size"), 0);
});

test("stepper updates totals, deletion can be undone, and clearing a draft requires confirmation", async () => {
  const app = setup(); await app.ready(); await app.add();
  const buttons = () => app.get("cart").querySelectorAll("button");
  const find = verb => buttons().find(button => button["aria-label"]?.startsWith(verb));
  assert.equal(find("Giảm").disabled, true);
  await find("Tăng").fire("click");
  assert.equal(app.read("cart.get(7).quantity"), 2);
  assert.equal(find("Giảm").disabled, false);
  await find("Xóa").fire("click");
  assert.equal(app.read("cart.size"), 0);
  assert.equal(app.get("undo-remove").hidden, false);
  await app.get("undo-remove").fire("click");
  assert.equal(app.read("cart.get(7).quantity"), 2);
  await app.get("clear-cart").fire("click");
  assert.equal(app.get("discard-dialog").open, true);
  assert.equal(app.read("cart.size"), 1);
  await app.get("discard-cancel").fire("click");
  assert.equal(app.read("cart.size"), 1);
  await app.get("clear-cart").fire("click");
  await app.get("discard-confirm").fire("click");
  assert.equal(app.read("cart.size"), 0);
  assert.equal(app.get("destination").value, "Cửa hàng");
});

test("backend field validation is shown inline and cleared when edited", async () => {
  const app = setup(); await app.ready(); await app.add();
  app.replies.set("/issues", () => app.reply({ destination: "Điểm nhận bị từ chối" }, 400, { errorCode: "VALIDATION_ERROR", message: "Dữ liệu không hợp lệ" }));
  await app.get("order-form").fire("submit");
  assert.equal(app.get("destination")["aria-invalid"], "true");
  assert.match(app.get("destination-error").textContent, /Điểm nhận bị từ chối/);
  assert.equal(app.get("destination").value, "Cửa hàng");
  await app.get("destination").fire("input");
  assert.equal(app.get("destination")["aria-invalid"], "false");
  assert.equal(app.get("destination-error").hidden, true);
});

test("core palette meets text and input-boundary contrast targets against white", () => {
  const css = fs.readFileSync(path.join(__dirname, "../../main/resources/static/pos.css"), "utf8");
  function luminance(hex) {
    const [r, g, b] = hex.match(/[a-f\d]{2}/gi).map(channel => parseInt(channel, 16) / 255)
      .map(channel => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }
  for (const [name, minimum] of [["primary", 4.5], ["muted", 4.5], ["danger", 4.5], ["border-strong", 3]]) {
    const color = css.match(new RegExp(`--${name}: #([a-f\\d]{6})`, "i"))[1];
    const ratio = 1.05 / (luminance(color) + 0.05);
    assert.ok(ratio >= minimum, `${name} contrast is ${ratio.toFixed(2)}, expected ${minimum}`);
  }
});

test("logout confirmation preserves the draft on cancel and backend failure", async () => {
  const app = setup(); await app.ready(); await app.add();
  await app.get("logout").fire("click");
  assert.equal(app.get("discard-dialog").open, true);
  await app.get("discard-cancel").fire("click");
  assert.equal(app.calls.filter(call => call.endpoint === "/auth/logout").length, 0);
  assert.equal(app.read("cart.size"), 1);
  app.replies.set("/auth/logout", () => app.reply(null, 500, { message: "Chưa đăng xuất được" }));
  await app.get("logout").fire("click");
  await app.get("discard-confirm").fire("click");
  assert.equal(app.read("cart.size"), 1);
  assert.equal(app.read("token"), "test-token");
  app.replies.set("/auth/logout", null);
  await app.get("logout").fire("click");
  await app.get("discard-confirm").fire("click");
  assert.equal(app.read("cart.size"), 0);
  assert.equal(app.get("login-panel").hidden, false);
  assert.equal(app.read("token"), "");
});

test("note textarea is closed and starts empty so subsequent controls stay outside it", () => {
  const html = fs.readFileSync(path.join(__dirname, "../../main/resources/static/index.html"), "utf8");
  const note = html.match(/<textarea\b[^>]*\bid="note"[^>]*>([\s\S]*?)<\/textarea>/);
  assert.ok(note, "Note textarea must have an explicit closing tag");
  assert.equal(note[1], "", "Markup must not become the default note value");
  const followingMarkup = html.slice(note.index + note[0].length);
  for (const id of ["note-error", "total", "submit-order", "order-message", "result", "discard-dialog"]) {
    assert.ok(followingMarkup.includes(`id="${id}"`), `${id} must be outside the textarea`);
  }
});

test("multiline note is submitted, kept on failure and cleared only after confirmation", async () => {
  const app = setup(); await app.ready(); await app.add();
  const note = "Giao tại cửa sau\nLiên hệ thủ kho trước khi đến";
  app.get("note").value = note;
  app.replies.set("/issues", () => app.reply(null, 400, { message: "Không đủ tồn kho" }));
  await app.get("order-form").fire("submit");
  assert.equal(app.calls.at(-1).body.note, note);
  assert.equal(app.get("note").value, note);
  assert.equal(app.read("cart.size"), 1);
  app.replies.set("/issues", { issueCode: "PXK-NOTE", status: "COMPLETED", warehouseName: "Kho", destination: "Cửa hàng", totalAmount: 12.5, items: [] });
  await app.get("order-form").fire("submit");
  assert.equal(app.get("note").value, "");
  assert.equal(app.get("result").hidden, false);
});

test("distinct item illustrations render offline and match the cart, with a safe fallback", async () => {
  const app = setup();
  const skus = ["VNM-100-1L", "ACK-HH-TC", "MSN-NN-500", "__proto__"];
  app.replies.set("/products", { content: skus.map((sku, index) => ({ ...app.product, id: index + 1, sku })), pageNo: 0, totalPages: 1, totalElements: 4 });
  await app.ready();
  const images = app.get("products").querySelectorAll("img");
  assert.equal(images.length, 4);
  assert.equal(new Set(images.map(image => image.src)).size, 4);
  for (const image of images) {
    assert.match(image.src, /^data:image\/svg\+xml;charset=utf-8,/);
    assert.match(decodeURIComponent(image.src), /<svg xmlns="http:\/\/www.w3.org\/2000\/svg"/);
    assert.match(image.alt, /hình minh họa/);
    assert.equal(image.loading, "lazy");
  }
  await app.add();
  assert.equal(app.get("cart").querySelectorAll("img")[0].src, images[0].src);
  assert.equal(app.read("cart.get(1).quantity"), 1);
});
