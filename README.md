# Smart Wardrobe Android MVP

A native Kotlin/Jetpack Compose app for a private two-person household wardrobe. The current project contains the offline-first UI shell and transparent local outfit matching engine; sample data makes it runnable before cloud setup.

## MVP decisions captured

- Google sign-in and a shared household with `Me` / `Wife` profiles
- Private clothing items: other household members see only `Private item`
- Photos stored in cloud; phone keeps only removable thumbnails
- Office weekly planning plus daily replacement, functions, and destination-based travel packing
- Weather-aware suggestions, using a selected home city and travel destination override
- Clothing, shoes, watches, belts, bags, and accessories are all catalogued from uploaded items
- Availability: available, laundry, dry-cleaning, packed, repair, unavailable
- Unlimited local recommendations; two AI-enhanced requests per household per day
- Notification preferences are independently switchable for daily outfit, weekly plan, laundry, purchases, and travel packing

## Next implementation slices

1. Replace `sampleWardrobe()` with Room persistence and an add-item/photo flow.
2. Add Firebase Authentication, Firestore, Storage, App Check, and household security rules.
3. Add weather, weekly plan, notification preferences, and purchase reminders.
4. Add a protected hosted-AI adapter behind the existing local matcher.

## Firebase setup (not committed)

Create a Firebase project, enable Google as an Authentication provider, create Firestore and Cloud Storage, then place the downloaded `google-services.json` in `app/`. Do not commit that file. Cloud Security Rules must verify that the signed-in user belongs to the requested household before any read or write.

The project intentionally does not request SMS access yet. That feature remains optional for a personal install because Google Play restricts SMS permissions for broadly distributed apps.
