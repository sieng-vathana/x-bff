# Marketplace API Guide

This guide covers the customer-facing marketplace API implemented in the backend.

Base path: `/api/v1/marketplace`

## Authentication and responses

Browsing routes are public. Cart, favorites, checkout, orders, and payments require a signed-in customer session. `GET /home` also accepts a session and includes the customer's summaries when signed in.

Responses use the shared envelope:

```json
{
  "status": 1,
  "code": 200,
  "message": "Success",
  "data": {}
}
```

Paged data contains `content`, `page` (zero-based), `size`, `totalElements`, `totalPages`, and `hasNext`.

## Where the data comes from

- Marketplace categories are active, global categories owned by platform business `businessId=1`. Backoffice staff manage them in `x-backoffice`; `x-product-service` serves them to the marketplace. Use each category's `id` as `categoryId`.
- Store category menus contain active global categories and categories mapped to that store.
- Product results contain active, sellable products with online prices. POS-only prices and internal cost prices are excluded.
- Store results contain approved, active, online stores. The BFF filters suspended merchants and merchants whose plans do not include marketplace access.
- Before checkout, the BFF loads current product and store data. The order service revalidates prices and reserves inventory.

## Home

### `GET /home`

Returns products, featured products, platform categories, a store directory page, account summary, favorite summary, and cart summary in one call. Product cards are already present in `data.products.content` for the first screen.

| Query | Default | Meaning |
| --- | ---: | --- |
| `search` | none | Searches products and store names/cities; maximum 120 characters. |
| `categoryId` | none | Filters products and featured products. |
| `page` | `0` | Product page. |
| `size` | `24` | Products per page; 1–100. |
| `storePage` | `0` | Store directory page. |
| `storeSize` | `24` | Stores per page; 1–100. |

Example: `GET /api/v1/marketplace/home?categoryId=12&page=0&size=24`

## Categories

### `GET /categories`

Returns active global marketplace categories. Query: `page` (default 0), `size` (default 100, max 100).

Category fields: `id`, `name`, `code`, optional `image`, and `featured`.

### `GET /stores/{storeId}/categories`

Returns active global categories plus categories mapped to the public store. Query: `page` (default 0), `size` (default 50, max 100).

## Product browsing

### `GET /products`

Returns paged product cards, each with its public store.

| Query | Default | Meaning |
| --- | ---: | --- |
| `search` | none | Searches product name, short name, code, description, and category name. |
| `categoryId` | none | Category ID from the category routes. |
| `storeId` | none | Restricts results to products available at that store, including global products. |
| `featuredOnly` | none | Set `true` to return featured products only. |
| `minPrice` / `maxPrice` | none | Online price range. |
| `inStock` | none | Set `true` to show available stock; non-stocked products remain available. |
| `sort` | `newest` | `newest` or `featured` (featured first, then newest). |
| `page` | `0` | Zero-based page. |
| `size` | `24` | Items per page; 1–100. |

Example: `GET /api/v1/marketplace/products?search=coffee&categoryId=12&inStock=true&page=0&size=24`

### `GET /products/{productId}`

Returns product detail, images, online variants, option attributes, and public store details. Optional `storeId` checks that the product is available at that store; use it for global products on a store page.

Variant fields include `variantId`, `name`, `image`, `onlinePrice`, `compareAtPrice`, `quantity`, `inStock`, and public option attributes. Cost price, supplier, and barcode are not exposed.

## Stores

### `GET /stores`

Returns the paged public store directory. Query: optional `search` (name or city), `page` (default 0), `size` (default 24, max 100).

### `GET /stores/{storeId}`

Returns public store fields: ID, name, code, store type, city, country, image, opening hours, and business ID.

### `GET /stores/{storeId}/products`

Returns products available at one store. Supports `search`, `categoryId`, `minPrice`, `maxPrice`, `inStock`, `sort`, `page`, and `size` as described above.

## Favorites

All favorite routes require sign-in.

### `GET /favorites`

Returns favorite product IDs, store IDs, and creation times for the signed-in customer.

### `POST /favorites`

Adds a favorite. Duplicate requests do not create duplicate entries.

Request:

```json
{ "productId": 501, "storeId": 31 }
```

`storeId` is optional when the product has a default store. The BFF verifies the product/store before saving.

### `DELETE /favorites/{productId}`

Removes the product from the signed-in customer's favorites. Returns `204 No Content`.

## Cart

All cart routes require sign-in. A cart uses one currency and may contain products from multiple stores. Checkout creates one order per store.

### `GET /cart`

Returns `itemCount` (sum of quantities), `subtotal`, `currency`, and `items`.

### `POST /cart/items`

Adds the requested quantity. If the variant is already present, quantities are added. The backend reads the current product name, thumbnail, online price, and currency.

Request:

```json
{
  "productId": 501,
  "storeId": 31,
  "variantId": 9001,
  "quantity": 1
}
```

Returns the updated cart summary.

### `PUT /cart/items/{variantId}`

Sets the quantity on an existing cart line.

Request: `{ "quantity": 2 }`

Returns the updated cart summary. Unknown lines return 404; unavailable stock returns 409.

### `DELETE /cart/items/{variantId}`

Removes that variant and returns the updated cart summary.

## Delivery and checkout

All checkout routes require sign-in.

### `POST /checkout/delivery-quotes`

Requests delivery options for a store and destination. Quotes expire after ten minutes.

Request:

```json
{
  "storeId": 31,
  "destinationLatitude": 11.5564,
  "destinationLongitude": 104.9282,
  "currencyCode": "USD"
}
```

The response contains quote `id`, `providerType`, `distanceKm`, `quotedFee`, `currencyCode`, `expiresAt`, and destination coordinates. Use the selected quote ID for checkout.

### `POST /checkout/orders`

Creates an order from all cart lines for the requested store, then removes those lines from the cart. Call once for each store if the cart contains items from several stores. The request contains no prices or totals; backend data supplies them.

Pickup uses `fulfillmentType: "CUSTOMER_PICKUP"`. Delivery uses `OWN_RIDER` or `THIRD_PARTY` and requires both a quote and address. Payment types are `COD` and `QR`; QR checkout currently supports USD.

Request example:

```json
{
  "storeId": 31,
  "fulfillmentType": "OWN_RIDER",
  "paymentType": "QR",
  "deliveryQuoteId": 701,
  "deliveryAddress": {
    "receiverName": "Sokha",
    "phone": "+85512345678",
    "provinceCity": "Phnom Penh",
    "district": "Chamkar Mon",
    "commune": "Tonle Bassac",
    "village": null,
    "streetAddress": "Street 123",
    "landmark": "Near the market",
    "deliveryInstructions": null,
    "latitude": 11.5564,
    "longitude": 104.9282
  },
  "discountCode": null,
  "note": null,
  "idempotencyKey": "checkout-20261107-001"
}
```

For pickup, `deliveryQuoteId` and `deliveryAddress` may be omitted. For delivery, both are required. The idempotency key is scoped to the signed-in customer.

## Guest checkout (no account)

Shoppers can buy without signing in. The cart is kept in the browser and sent with the order. All routes below are public; the buyer's contact name and phone are required for both delivery and pickup so the store can reach them.

### `POST /guest/checkout/delivery-quotes`

Same request and response as `POST /checkout/delivery-quotes`, without sign-in.

### `POST /guest/checkout/orders`

Same fields as `POST /checkout/orders`, plus `items` (`productId`, `variantId`, `quantity`; 1–50 lines), `contactName` and `contactPhone`. The BFF checks each variant against the store's current catalog and stock; the order service recalculates prices and reserves stock.

Response `data`: `{ "order": { …order… }, "guestToken": "…" }`. Keep the token: it is the only way to open the order again. Tokens are valid for 30 days.

### `GET /guest/orders/{orderId}`, `GET /guest/orders/{orderId}/payments`, `POST /guest/orders/{orderId}/payment/qr`

Same as the signed-in order routes. Send the token in the `X-Guest-Order-Token` header. A wrong or expired token returns 404.

Signed-in checkout also accepts optional `contactName` and `contactPhone`, stored on the order for the store to call.

## Orders and payments

All routes below require sign-in. Order results are scoped to the signed-in customer; another customer's order is returned as not found.

### `GET /orders`

Returns the customer's paged online order history. Query: `page` (default 0) and `size` (default 20, max 100).

### `GET /orders/{orderId}`

Returns one customer-owned online order, including current status and order lines.

### `GET /orders/{orderId}/payments`

Returns payments for that order after checking customer ownership.

### `POST /orders/{orderId}/payment/qr`

Creates or reuses a QR payment for an unpaid order owned by the customer. The configured KHQRPay integration supports USD only. The response contains QR/payment details; the provider reports payment completion.

## Typical screen flow

1. Call `GET /home` to render categories, featured products, product cards, and stores immediately.
2. Use `GET /products` or `GET /stores/{storeId}/products` for search, filters, and pagination.
3. Call `GET /products/{productId}` to show product detail and choose a variant.
4. Add variants with `POST /cart/items` and refresh with `GET /cart`.
5. For delivery, request quotes, select one, then create an order for that store.
6. For QR payment, call `POST /orders/{orderId}/payment/qr` after order creation.

`market-ui` calls these routes: signed-in shoppers use the cart routes, guests use the `/guest` routes.

