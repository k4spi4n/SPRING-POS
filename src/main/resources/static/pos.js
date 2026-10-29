"use strict";

const $ = (id) => document.getElementById(id);
const currency = new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 2 });
const cart = new Map();
// shortcut: minh họa theo SKU có sẵn; dùng imageUrl từ API khi có thư viện ảnh sản phẩm.
const productPictures = new Map([["VNM-100-1L", "milk"], ["ACK-HH-TC", "noodles"], ["MSN-NN-500", "sauce"]]);
const illustrations = {
  milk: `<defs><linearGradient id="carton" x2="1" y2="1"><stop stop-color="#fff"/><stop offset="1" stop-color="#e0edf8"/></linearGradient></defs>
    <ellipse cx="161" cy="218" rx="64" ry="9" fill="#dce7f0"/>
    <path d="M111 74 137 39h57l22 35v130a8 8 0 0 1-8 8h-89a8 8 0 0 1-8-8Z" fill="url(#carton)" stroke="#9fb9cf" stroke-width="2"/>
    <path d="m111 74 26-35h57l22 35Z" fill="#5d9bce"/><path d="M194 39v35h22Z" fill="#3675a7"/>
    <path d="M194 74h22v130a8 8 0 0 1-8 8h-14Z" fill="#d2e3f1"/>
    <ellipse cx="166" cy="58" rx="13" ry="8" fill="#f9fcff" stroke="#a5c6dd" stroke-width="2"/>
    <rect x="121" y="96" width="63" height="77" rx="11" fill="#2379b4"/>
    <path d="M153 109c-5 9-19 23-19 34a19 19 0 0 0 38 0c0-11-14-25-19-34" fill="#fff"/>
    <text x="153" y="196" fill="#27618a" font-family="Arial,sans-serif" font-weight="700" font-size="18" text-anchor="middle">SỮA</text>`,
  noodles: `<ellipse cx="160" cy="213" rx="100" ry="9" fill="#f0dfe4"/>
    <path d="m58 65 8-7 8 7 8-7 8 7 8-7h124l8 7 8-7 8 7 8-7 8 7v130l-8 7-8-7-8 7-8-7-8 7H98l-8-7-8 7-8-7-8 7-8-7Z" fill="#ef7197" stroke="#cb4673" stroke-width="2"/>
    <path d="M73 77h174v108H73Z" fill="#f893b1"/>
    <ellipse cx="160" cy="111" rx="67" ry="32" fill="#fff7dd"/>
    <text x="160" y="122" fill="#bc183d" font-family="Arial,sans-serif" font-weight="700" font-size="34" text-anchor="middle">MÌ</text>
    <path d="M106 159h108q-9 43-54 43t-54-43" fill="#fff" stroke="#ca4154" stroke-width="2"/>
    <ellipse cx="160" cy="160" rx="55" ry="13" fill="#ecbd52"/>
    <path d="M120 151q10-11 20 0t20 0 20 0 20 0M119 161q10-10 20 0t20 0 20 0 20 0" fill="none" stroke="#fff0ad" stroke-width="4"/>
    <path d="m182 148 13-12 7 18" fill="#4e9560"/>`,
  sauce: `<defs><linearGradient id="sauce" x2="1"><stop stop-color="#7c341d"/><stop offset=".45" stop-color="#bb672c"/><stop offset="1" stop-color="#71301c"/></linearGradient></defs>
    <ellipse cx="160" cy="220" rx="53" ry="8" fill="#eddfd6"/>
    <path d="M144 47h32v29q0 8 12 17t12 23v85q0 15-15 15h-50q-15 0-15-15v-85q0-14 12-23t12-17Z" fill="url(#sauce)" stroke="#71301c" stroke-width="2"/>
    <rect x="137" y="25" width="46" height="28" rx="5" fill="#c52d32"/><path d="M145 29v20m8-20v20m8-20v20m8-20v20m8-20v20" stroke="#e55d59" stroke-width="2"/>
    <path d="M140 102q-7 8-7 20v68" fill="none" stroke="#da9860" stroke-width="5" stroke-linecap="round"/>
    <rect x="124" y="119" width="72" height="69" rx="6" fill="#ffe8a8"/>
    <rect x="124" y="119" width="72" height="20" rx="4" fill="#c52d32"/>
    <text x="160" y="158" fill="#873325" font-family="Arial,sans-serif" font-weight="700" font-size="13" text-anchor="middle">NƯỚC MẮM</text>
    <path d="M145 174q12-13 25 0-13 13-25 0m25 0 8-8v16Z" fill="#b55231"/>`,
  product: `<ellipse cx="160" cy="215" rx="76" ry="9" fill="#e9e0e4"/>
    <path d="m81 91 79-39 79 39v94l-79 35-79-35Z" fill="#e8c9a9" stroke="#ad8765" stroke-width="2"/>
    <path d="m81 91 79 37 79-37m-79 37v92" fill="none" stroke="#ad8765" stroke-width="2"/>
    <path d="m133 65 80 39-24 11-80-39Z" fill="#c8101e"/><path d="m189 115 24-11v39l-24 11Z" fill="#ae0b18"/>
    <path d="m100 151 39 18m-39-6 24 11" stroke="#fff8ef" stroke-width="6" stroke-linecap="round"/>`
};
let token = ""; // JWT chỉ giữ trong bộ nhớ của tab, không lưu cùng mật khẩu vào storage.
let products = [];
let page = 0;
let totalPages = 0;
let search = "";
let catalogRequest = 0;
let loadingProducts = false;
let submitting = false;
let removedItem = null;
let discardAction = null;
let loggingOut = false;

function message(id, text = "", error = false) {
  const element = $(id);
  element.textContent = text;
  element.hidden = !text;
  element.classList.toggle("error", error);
  element.setAttribute("role", error ? "alert" : "status");
}

function fieldError(input, text = "") {
  input.setAttribute("aria-invalid", String(Boolean(text)));
  input.setCustomValidity(text);
  const feedback = $(`${input.id}-error`);
  feedback.textContent = text ? `Lỗi: ${text}` : "";
  feedback.hidden = !text;
}

function showValidation(error) {
  const names = { warehouseId: "warehouse", destination: "destination", note: "note", username: "username", password: "password" };
  for (const [key, text] of Object.entries(error.fields || {})) {
    const quantity = key.match(/^items\[(\d+)\]\.quantity$/);
    const id = quantity ? `quantity-${Array.from(cart.keys())[Number(quantity[1])]}` : names[key];
    if (id && $(id)) fieldError($(id), text);
  }
}

function forgetRemoval() {
  removedItem = null;
  $("undo-remove").hidden = true;
  $("result").hidden = true;
}

function confirmDiscard(description, action) {
  if (submitting || $("discard-dialog").open) return;
  discardAction = action;
  $("discard-description").textContent = description;
  $("discard-dialog").showModal();
}

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

function productImage(product) {
  const figure = element("figure", undefined, "product-picture");
  const image = element("img");
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 240">${illustrations[productPictures.get(product.sku) || "product"]}</svg>`;
  Object.assign(image, {
    src: `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`,
    alt: `${product.name} — hình minh họa`, loading: "lazy", decoding: "async", width: 320, height: 240
  });
  figure.append(image, element("figcaption", "Hình minh họa"));
  return figure;
}

function showLogin(text = "") {
  token = "";
  catalogRequest++;
  $("workspace").hidden = true;
  $("workspace-nav").hidden = true;
  $("login-panel").hidden = false;
  $("logout").hidden = true;
  $("identity").textContent = "Chưa đăng nhập";
  message("login-message", text, Boolean(text));
  $("username").focus();
}

async function api(path, { method = "GET", body } = {}) {
  const requestToken = token;
  let response;
  let payload;
  try {
    response = await fetch(`api/v1${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(requestToken ? { Authorization: `Bearer ${requestToken}` } : {})
      },
      body: body ? JSON.stringify(body) : undefined,
      credentials: "omit",
      cache: "no-store",
      signal: AbortSignal.timeout(30000)
    });
    payload = await response.json();
  } catch {
    const uncertain = method === "POST" && path === "/issues"
      ? " Chưa có xác nhận; đơn có thể đã được tạo. Kiểm tra phiếu xuất trước khi gửi lại."
      : " Hãy kiểm tra kết nối và thử lại.";
    throw new Error(`Không nhận được phản hồi hợp lệ từ máy chủ.${uncertain}`);
  }
  if (!response.ok || !payload.success) {
    if (response.status === 401 && requestToken && requestToken === token) {
      showLogin("Phiên đăng nhập hết hiệu lực. Đăng nhập lại để tiếp tục; giỏ hàng vẫn được giữ.");
    }
    const details = payload.errorCode === "VALIDATION_ERROR" && payload.data
      ? Object.values(payload.data).join("\n") : "";
    const error = new Error([payload.message || `Yêu cầu thất bại (HTTP ${response.status}).`, details,
      payload.requestId ? `Mã tra cứu: ${payload.requestId}` : ""].filter(Boolean).join("\n"));
    error.fields = payload.errorCode === "VALIDATION_ERROR" ? payload.data : null;
    throw error;
  }
  return payload.data;
}

function updateControls() {
  $("order-fields").disabled = submitting;
  $("logout").disabled = submitting || loggingOut;
  $("clear-cart").disabled = submitting || !cart.size;
  $("reload").disabled = submitting;
  $("order-form").setAttribute("aria-busy", String(submitting));
  $("submit-order").disabled = submitting || cart.size === 0 || !$("warehouse").value || !token;
  $("submit-order").textContent = submitting ? "Đang tạo đơn…" : "Tạo đơn xuất kho";
  $("previous").disabled = loadingProducts || page <= 0;
  $("next").disabled = loadingProducts || page + 1 >= totalPages;
  for (const button of $("products").querySelectorAll("button")) {
    button.disabled = submitting || button.dataset.unavailable === "true";
  }
}

function renderCart() {
  $("result").hidden = true;
  $("cart").replaceChildren();
  if (!cart.size) {
    const empty = element("div", undefined, "empty-state");
    const symbol = element("span", "+", "empty-symbol");
    symbol.setAttribute("aria-hidden", "true");
    empty.append(symbol, element("h3", "Giỏ hàng đang trống"), element("p", "Chọn sản phẩm ở danh mục để bắt đầu."));
    $("cart").append(empty);
  }
  for (const [id, item] of cart) {
    const row = element("div", undefined, "cart-item");
    const heading = element("div", undefined, "cart-item-head");
    const controls = element("div", undefined, "cart-controls");
    const quantityBox = element("div", undefined, "quantity");
    const label = element("label", "Số lượng");
    label.htmlFor = `quantity-${id}`;
    const input = element("input");
    Object.assign(input, { id: label.htmlFor, type: "number", min: "1", max: "2147483647", step: "1", required: true, value: item.quantity });
    input.setAttribute("aria-label", `Số lượng ${item.name}`);
    input.setAttribute("aria-describedby", `quantity-${id}-error`);
    const feedback = element("p", "", "field-error");
    feedback.id = `quantity-${id}-error`;
    feedback.setAttribute("aria-live", "polite");
    feedback.hidden = true;
    input.addEventListener("input", () => {
      const quantity = input.valueAsNumber;
      const valid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 2147483647;
      fieldError(input, valid ? "" : "Nhập số nguyên từ 1 đến 2147483647.");
      minus.disabled = !valid || quantity <= 1;
      plus.disabled = !valid || quantity >= 2147483647;
      if (!valid || submitting) return;
      forgetRemoval();
      item.quantity = quantity;
      subtotal.textContent = currency.format(quantity * (item.sellingPrice ?? 0));
      renderTotal();
    });
    const remove = element("button", "×", "remove");
    remove.type = "button";
    remove.setAttribute("aria-label", `Xóa ${item.name}`);
    remove.addEventListener("click", () => {
      if (submitting) return;
      removedItem = { ...item };
      cart.delete(id);
      renderCart();
      $("undo-remove").hidden = false;
      message("order-message", `Đã xóa ${item.name}. Có thể hoàn tác ngay bên dưới giỏ hàng.`);
      $("order-title").tabIndex = -1;
      $("order-title").focus();
    });
    const subtotal = element("span", currency.format(item.quantity * (item.sellingPrice ?? 0)), "cart-subtotal");
    const stepper = element("div", undefined, "stepper");
    const minus = element("button", "−");
    const plus = element("button", "+");
    for (const [button, change, verb] of [[minus, -1, "Giảm"], [plus, 1, "Tăng"]]) {
      button.type = "button";
      button.setAttribute("aria-label", `${verb} số lượng ${item.name}`);
      button.addEventListener("click", () => {
        if (submitting || !input.reportValidity()) return;
        const quantity = item.quantity + change;
        if (quantity < 1 || quantity > 2147483647) return;
        input.value = quantity;
        input.dispatchEvent(new Event("input"));
      });
    }
    stepper.append(minus, input, plus);
    minus.disabled = item.quantity <= 1;
    plus.disabled = item.quantity >= 2147483647;
    quantityBox.append(label, stepper);
    const info = element("div", undefined, "cart-product-info");
    info.append(element("h3", item.name), element("p", `${item.sku} · ${currency.format(item.sellingPrice ?? 0)} / ${item.unit}`));
    const product = element("div", undefined, "cart-product");
    product.append(productImage(item), info);
    heading.append(product, remove);
    controls.append(quantityBox, subtotal);
    row.append(heading, controls, feedback);
    $("cart").append(row);
  }
  renderTotal();
  updateControls();
}

function renderTotal() {
  let total = 0;
  let count = 0;
  for (const item of cart.values()) {
    total += item.quantity * (item.sellingPrice ?? 0);
    count += item.quantity;
  }
  $("total").textContent = currency.format(total);
  $("cart-count").textContent = count;
  $("cart-summary").textContent = cart.size ? `${cart.size} mặt hàng · ${count} đơn vị` : "Chưa có sản phẩm";
  for (const card of $("products").querySelectorAll("article")) {
    const quantity = cart.get(Number(card.dataset.productId))?.quantity || 0;
    card.classList.toggle("selected", quantity > 0);
    card.querySelector(".product-picked").textContent = quantity ? `Đã chọn: ${quantity}` : "";
  }
}

function renderProducts() {
  $("products").replaceChildren();
  for (const product of products) {
    const card = element("article", undefined, "product");
    card.dataset.productId = product.id;
    const top = element("div", undefined, "product-top");
    top.append(element("span", product.unit, "product-unit"), element("span", "", "product-picked"));
    card.append(productImage(product), top, element("p", product.categoryName, "category"), element("h3", product.name), element("p", product.sku, "sku"));
    const price = element("p", currency.format(product.sellingPrice ?? 0), "price");
    price.append(element("span", ` / ${product.unit}`));
    const button = element("button", product.active ? "+ Thêm vào giỏ" : "Ngừng kinh doanh");
    button.type = "button";
    button.dataset.unavailable = String(!product.active);
    button.setAttribute("aria-label", `${product.active ? "Thêm" : "Ngừng kinh doanh:"} ${product.name}`);
    button.addEventListener("click", () => {
      if (submitting || !product.active) return;
      for (const input of $("cart").querySelectorAll("input")) if (!input.reportValidity()) return;
      const current = cart.get(product.id);
      if (current && current.quantity === 2147483647) return;
      forgetRemoval();
      cart.set(product.id, { ...product, quantity: (current?.quantity ?? 0) + 1 });
      renderCart();
      message("order-message", `Đã thêm ${product.name} vào giỏ hàng.`);
    });
    card.append(price, button);
    $("products").append(card);
  }
  renderTotal();
  updateControls();
}

async function loadProducts() {
  const request = ++catalogRequest;
  loadingProducts = true;
  $("products").replaceChildren();
  $("page-info").textContent = "";
  message("catalog-message", "Đang tải sản phẩm…");
  $("catalog-message").dataset.loading = "true";
  $("catalog-empty").hidden = true;
  $("catalog-retry").hidden = true;
  $("reset-search").hidden = !search && !$("category").value;
  $("products").setAttribute("aria-busy", "true");
  updateControls();
  try {
    const params = new URLSearchParams({ search, page, size: 12, sort: $("sort").value || "name,asc" });
    if ($("category").value) params.set("categoryId", $("category").value);
    const data = await api(`/products?${params}`);
    if (request !== catalogRequest) return;
    products = data.content;
    page = data.pageNo;
    totalPages = data.totalPages;
    renderProducts();
    message("catalog-message", products.length ? "" : "Không có sản phẩm phù hợp.");
    $("catalog-empty").hidden = products.length > 0;
    $("catalog-count").textContent = `${data.totalElements} sản phẩm`;
    $("page-info").textContent = totalPages ? `${page + 1} / ${totalPages} · ${data.totalElements} sản phẩm` : "0 sản phẩm";
  } catch (error) {
    if (request === catalogRequest) {
      message("catalog-message", error.message, true);
      $("catalog-count").textContent = "Chưa tải được danh mục";
      $("catalog-retry").hidden = false;
    }
  } finally {
    if (request === catalogRequest) {
      loadingProducts = false;
      $("catalog-message").dataset.loading = "false";
      $("products").setAttribute("aria-busy", "false");
      updateControls();
    }
  }
}

async function loadCategories() {
  const requestToken = token;
  const selected = $("category").value;
  $("category").disabled = true;
  message("category-message");
  try {
    const categories = await api("/categories");
    if (requestToken !== token) return;
    $("category").replaceChildren(new Option("Tất cả nhóm hàng", ""));
    for (const category of categories) $("category").append(new Option(category.name, category.id));
    $("category").value = selected;
    $("category").disabled = false;
  } catch (error) {
    if (requestToken === token) message("category-message", `${error.message}\nChọn Tải lại để tải bộ lọc. Bạn vẫn có thể tìm kiếm sản phẩm.`, true);
  }
}

async function loadWarehouses() {
  const requestToken = token;
  const selected = $("warehouse").value;
  $("warehouse").replaceChildren(new Option("Chọn kho xuất", ""));
  message("warehouse-message", "Đang tải kho…");
  updateControls();
  try {
    const warehouses = await api("/warehouses/active");
    if (requestToken !== token) return;
    for (const warehouse of warehouses) $("warehouse").append(new Option(`${warehouse.code} · ${warehouse.name}`, warehouse.id));
    $("warehouse").value = selected;
    message("warehouse-message", warehouses.length ? "" : "Chưa có kho hoạt động. Liên hệ quản lý để bổ sung kho.");
  } catch (error) {
    if (requestToken === token) message("warehouse-message", error.message, true);
  } finally {
    updateControls();
  }
}

$("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if ($("login-fields").disabled || !$("login-form").reportValidity()) return;
  $("login-fields").disabled = true;
  $("login-button").textContent = "Đang đăng nhập…";
  message("login-message");
  try {
    const user = await api("/auth/login", { method: "POST", body: { username: $("username").value.trim(), password: $("password").value } });
    token = user.token;
    $("password").value = "";
    const roles = { ROLE_STAFF: "Nhân viên", ROLE_MANAGER: "Thủ kho", ROLE_ADMIN: "Quản trị viên" };
    $("identity").textContent = `${user.fullName} · ${roles[user.role] || user.role}`;
    $("login-panel").hidden = true;
    $("workspace").hidden = false;
    $("workspace-nav").hidden = false;
    $("logout").hidden = false;
    renderCart();
    $("search").focus();
    await Promise.all([loadProducts(), loadWarehouses(), loadCategories()]);
  } catch (error) {
    showValidation(error);
    message("login-message", error.message, true);
  } finally {
    $("login-fields").disabled = false;
    $("login-button").textContent = "Đăng nhập";
  }
});

async function logout() {
  if (submitting || loggingOut) return;
  loggingOut = true;
  $("logout").disabled = true;
  try {
    await api("/auth/logout", { method: "POST" });
    cart.clear();
    forgetRemoval();
    renderCart();
    $("order-form").reset();
    for (const id of ["warehouse", "destination", "note"]) fieldError($(id));
    $("result").hidden = true;
    message("order-message");
    showLogin();
  } catch (error) {
    message(token ? "order-message" : "login-message", error.message, true);
  } finally {
    loggingOut = false;
    $("logout").disabled = false;
  }
}

$("logout").addEventListener("click", () => {
  if (cart.size) confirmDiscard("Đăng xuất sẽ xóa giỏ hàng và thông tin xuất kho chưa gửi. Bạn có muốn tiếp tục?", logout);
  else void logout();
});

$("search-form").addEventListener("submit", (event) => {
  event.preventDefault();
  search = $("search").value.trim();
  page = 0;
  void loadProducts();
});
$("previous").addEventListener("click", () => { if (page > 0 && !loadingProducts) { page--; void loadProducts(); } });
$("next").addEventListener("click", () => { if (page + 1 < totalPages && !loadingProducts) { page++; void loadProducts(); } });
$("reload").addEventListener("click", () => { void loadProducts(); void loadWarehouses(); void loadCategories(); });
$("catalog-retry").addEventListener("click", () => { void loadProducts(); });
for (const id of ["category", "sort"]) $(id).addEventListener("change", () => { page = 0; void loadProducts(); });
for (const id of ["reset-search", "empty-reset"]) $(id).addEventListener("click", () => {
  search = ""; page = 0;
  $("search").value = "";
  $("category").value = "";
  void loadProducts();
  $("search").focus();
});
$("warehouse").addEventListener("change", updateControls);

$("order-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (submitting || !token || !cart.size || !$("order-form").reportValidity()) return;
  const destination = $("destination").value.trim();
  if (!destination) {
    fieldError($("destination"), "Nơi nhận / Điểm đến không được để trống.");
    message("order-message", "Nơi nhận / Điểm đến không được để trống.", true);
    $("destination").focus();
    return;
  }
  submitting = true;
  forgetRemoval();
  updateControls();
  message("order-message", "Đang gửi đơn. Vui lòng chờ xác nhận…");
  $("result").hidden = true;
  try {
    const issue = await api("/issues", { method: "POST", body: {
      warehouseId: Number($("warehouse").value), destination, issueType: "STORE_TRANSFER", note: $("note").value.trim(),
      items: Array.from(cart.values(), (item) => ({ productId: item.id, quantity: item.quantity }))
    } });
    const content = $("result-content");
    content.replaceChildren(element("p", `Mã phiếu: ${issue.issueCode} · ${issue.status === "COMPLETED" ? "Hoàn thành" : issue.status}`), element("p", `${issue.warehouseName} → ${issue.destination}`));
    content.append(element("strong", `Tổng xác nhận: ${currency.format(issue.totalAmount)}`));
    const details = element("details");
    details.append(element("summary", `Xem chi tiết ${issue.items.length} dòng lô hàng`));
    const scroll = element("div", undefined, "table-scroll");
    scroll.tabIndex = 0;
    scroll.setAttribute("role", "region");
    scroll.setAttribute("aria-label", "Chi tiết lô hàng đã xuất, cuộn ngang để xem đủ cột");
    const table = element("table");
    const caption = element("caption", "Chi tiết lô hàng đã xuất", "sr-only");
    const head = element("thead");
    const header = element("tr");
    for (const [text, className] of [["Sản phẩm / Lô", ""], ["SL", "number"], ["Đơn giá", "money"], ["Thành tiền", "money"]]) {
      const cell = element("th", text, className);
      cell.scope = "col";
      header.append(cell);
    }
    head.append(header);
    const rows = element("tbody");
    for (const item of issue.items) {
      const row = element("tr");
      row.append(element("td", `${item.productName} · ${item.unit}\nLô ${item.batchNumber} · HSD ${item.expiryDate}`), element("td", item.quantity, "number"), element("td", currency.format(item.unitPrice), "money"), element("td", currency.format(item.subTotal), "money"));
      rows.append(row);
    }
    table.append(caption, head, rows);
    scroll.append(table);
    details.append(scroll);
    content.append(details);
    cart.clear();
    renderCart();
    $("note").value = "";
    message("order-message");
    $("result").hidden = false;
    $("result").focus();
  } catch (error) {
    showValidation(error);
    message("order-message", `${error.message}\nGiỏ hàng được giữ lại.`, true);
  } finally {
    submitting = false;
    updateControls();
  }
});

for (const id of ["username", "password", "warehouse", "destination", "note"]) {
  const input = $(id);
  input.addEventListener("invalid", () => {
    const labels = { username: "Tên đăng nhập", password: "Mật khẩu", warehouse: "Kho xuất", destination: "Nơi nhận / Điểm đến", note: "Ghi chú" };
    fieldError(input, input.validity.valueMissing ? `${labels[id]} không được để trống.` : input.validationMessage);
  });
  input.addEventListener("input", () => fieldError(input));
  input.addEventListener("change", () => fieldError(input));
}

$("undo-remove").addEventListener("click", () => {
  if (!removedItem || submitting) return;
  const item = removedItem;
  cart.set(item.id, item);
  forgetRemoval();
  renderCart();
  message("order-message", `Đã khôi phục ${item.name}.`);
  $(`quantity-${item.id}`).focus();
});
$("clear-cart").addEventListener("click", () => {
  if (!cart.size) return;
  confirmDiscard("Toàn bộ sản phẩm trong giỏ sẽ bị xóa. Thông tin kho và nơi nhận vẫn được giữ.", () => {
    cart.clear(); forgetRemoval(); renderCart();
    message("order-message", "Đã xóa giỏ hàng. Chọn sản phẩm để tạo đơn mới.");
    $("search").focus();
  });
});
$("discard-cancel").addEventListener("click", () => $("discard-dialog").close());
$("discard-dialog").addEventListener("close", () => { discardAction = null; });
$("discard-dialog").addEventListener("cancel", () => { discardAction = null; });
$("discard-confirm").addEventListener("click", async () => {
  const action = discardAction;
  $("discard-dialog").close();
  if (action) await action();
});
globalThis.addEventListener?.("beforeunload", (event) => {
  if (cart.size || submitting) { event.preventDefault(); event.returnValue = ""; }
});

renderCart();
