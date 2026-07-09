# Firebase setup cho MapSupervision

Project Firebase dang dung:

- Project ID: `mapsupervision`
- Project number: `735767087959`
- Android app: `1:735767087959:android:ea6eecaafb9f124b388c12`
- Web app: `1:735767087959:web:bdda438874b385b4388c12`

Da hoan thanh:

- Firestore rules da deploy
- Firestore indexes da deploy
- `app/google-services.json` da them vao Android app
- `webapp/.env.local` da tao
- Android da build pass voi `:app:assembleDebug`
- Webapp da build pass voi `npm run build`

## 1. Bat Authentication trong Firebase Console

Trang can mo:

- `https://console.firebase.google.com/project/mapsupervision/authentication`

Can lam:

1. Bam `Get started` neu chua khoi tao Authentication.
2. Vao tab `Sign-in method`.
3. Bat `Email/Password`.
4. Khong can bat `Anonymous` cho webapp dang nhap/dang ky bang email.
5. Vao tab `Templates` > `Email address verification` de kiem tra email kich hoat.
6. Trong `Settings` > `Authorized domains`, dam bao co `localhost`, `127.0.0.1` va domain deploy production neu co.

Luu y:

- Goi REST API admin de bat Auth bi tra ve `BILLING_NOT_ENABLED` cho `identityPlatform:initializeAuth`.
- Vi vay buoc nay can lam bang Firebase Console.

## 2. Khoi tao Firebase Storage

Trang can mo:

- `https://console.firebase.google.com/project/mapsupervision/storage`

Can lam:

1. Bam `Get started`.
2. Chon bucket location.
3. Hoan tat wizard.

Bucket mong doi:

- `mapsupervision.firebasestorage.app`

Luu y:

- `firebase deploy --only storage --project mapsupervision` se that bai neu chua khoi tao Storage lan dau.

## 3. Deploy lai sau khi xong 2 buoc tren

Chay tai root project:

```powershell
firebase deploy --only firestore:rules,firestore:indexes,storage --project mapsupervision
```

## 4. Chay webapp local

```powershell
cd webapp
npm run dev -- --hostname 127.0.0.1 --port 3000
```

Mo:

- `http://127.0.0.1:3000`

## 5. File cau hinh da co

- Android Firebase config: `app/google-services.json`
- Android env local: `.env`
- Web env local: `webapp/.env.local`
- Firebase config: `firebase.json`
- Firestore rules: `firestore.rules`
- Firestore indexes: `firestore.indexes.json`
- Storage rules: `storage.rules`

## 5.1. Google Drive media sync

Android media upload uses the webapp API route and stores the returned public Google Drive URL in `site_photos.remoteUrl`.

Android `.env`:

```properties
MEDIA_UPLOAD_BASE_URL=http://127.0.0.1:3000
```

Web `webapp/.env.local`:

```properties
FIREBASE_SERVICE_ACCOUNT_JSON={...}
GOOGLE_DRIVE_ROOT_FOLDER_ID=...
GOOGLE_SERVICE_ACCOUNT_JSON={...}
```

Requirements:

- The Firebase service account can verify ID tokens and read `projects/{projectId}/projectMembers`.
- The Google service account has writer access to `GOOGLE_DRIVE_ROOT_FOLDER_ID`.
- Uploaded Drive files are shared as public reader files so Android and webapp can display the public URL.
- The route creates folders under the root as `{projectId}/photos/Nodes`, `{projectId}/photos/Routes`, `{projectId}/media/videos/Nodes`, and `{projectId}/media/videos/Routes`.

## 6. Bootstrap global admin claim

Ba tai khoan global admin co dinh:

- `buiducthanh2@gmail.com`
- `cahn2023@gmail.com`
- `thanh.bd@tfsc.com.vn`

Script bootstrap:

```powershell
cd webapp
npm install
npm run bootstrap:admins
```

Yeu cau:

- May chay script phai co quyen Admin SDK.
- Co the dung `GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json`
- Hoac set `FIREBASE_SERVICE_ACCOUNT_JSON` bang JSON service account.

Tinh chat van hanh:

- Script idempotent, chay lai an toan.
- Neu email da co `customClaims.admin = true` thi script se bao `UNCHANGED`.
- Neu user chua ton tai trong Firebase Auth thi script se bao `FAILED`.

Sau khi gan claim:

- User can dang xuat va dang nhap lai tren webapp/Android de lay token moi.
- Webapp se doc `idTokenResult.claims.admin === true` de hien giao dien admin.
