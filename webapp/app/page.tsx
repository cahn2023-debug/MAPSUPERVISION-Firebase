"use client";

import { useEffect, useMemo, useState } from "react";
import type { FirebaseError } from "firebase/app";
import dynamic from "next/dynamic";
import { db, firebaseReady, getFirebaseUserAdminClaim, observeAuth, registerWithEmail, sendVerificationEmail, signInWithEmail, signOutCurrentUser, type FirebaseUser } from "@/lib/firebase";
import {
  createDailyLogDocument,
  deleteProjectMemberRecord,
  createProjectDocument,
  createTaskDocument,
  emptyProjectCollections,
  saveProjectMember,
  subscribeCurrentProjectMember,
  subscribeProjectDocument,
  subscribeProjectMembers,
  subscribeProjects,
  subscribeProjectTable,
  subscribeUsersDirectory,
  syncTables,
  upsertUserProfile,
  updateTaskStatusDocument,
  type ContractorScope,
  type ProjectCollections,
  type ProjectDoc,
  type ProjectDraft,
  type ProjectMemberRow,
  type SitePhotoRow,
  type TaskRow,
  type UserProfileRow
} from "@/lib/sync";

type TaskDraft = {
  title: string;
  description: string;
};

type DailyLogDraft = {
  workItem: string;
  note: string;
  manpower: string;
  volume: string;
  unit: string;
  categoryName: string;
  weather: string;
};

type AuthMode = "signIn" | "register";

const GisWebMap = dynamic(
  () => import("@/components/GisWebMap").then((module) => module.GisWebMap),
  {
    ssr: false,
    loading: () => <div className="web-map-empty empty-state">Đang tải bản đồ...</div>
  }
);

const emptyTask: TaskDraft = { title: "", description: "" };
const emptyDailyLog: DailyLogDraft = {
  workItem: "",
  note: "",
  manpower: "0",
  volume: "0",
  unit: "m",
  categoryName: "",
  weather: ""
};

const emptyProjectDraft: ProjectDraft = { name: "", projectCode: "" };

function errorMessage(error: unknown): string {
  const firebaseError = error as FirebaseError;
  if (firebaseError?.code === "permission-denied") {
    return "Tài khoản chưa được cấp quyền cho dự án này.";
  }
  if (firebaseError?.code === "auth/invalid-credential") {
    return "Email hoặc mật khẩu không đúng.";
  }
  if (firebaseError?.code === "auth/configuration-not-found") {
    return "Firebase Authentication chưa được khởi tạo cho project này. Vào Firebase Console > Authentication > Get started, rồi bật Email/Password trong Sign-in method.";
  }
  if (firebaseError?.code === "auth/operation-not-allowed") {
    return "Firebase chưa bật phương thức đăng nhập Email/Password. Vào Authentication > Sign-in method để bật Email/Password.";
  }
  if (firebaseError?.code === "auth/email-already-in-use") {
    return "Email này đã được đăng ký. Vui lòng đăng nhập hoặc sử dụng email khác.";
  }
  if (firebaseError?.code === "auth/weak-password") {
    return "Mật khẩu cần có ít nhất 6 ký tự.";
  }
  if (firebaseError?.code === "auth/invalid-email") {
    return "Email không hợp lệ.";
  }
  if (firebaseError?.code === "auth/too-many-requests") {
    return "Bạn thao tác quá nhiều lần. Vui lòng chờ một lúc rồi thử lại.";
  }
  return firebaseError?.message ?? "Có lỗi xảy ra trong quá trình kết nối.";
}

function formatDateTime(value: unknown): string {
  const millis = Number(value ?? 0);
  if (!Number.isFinite(millis) || millis <= 0) return "-";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(millis));
}

function formatNumber(value: unknown, suffix = ""): string {
  const number = Number(value ?? 0);
  if (!Number.isFinite(number)) return suffix ? `0 ${suffix}` : "0";
  return `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 }).format(number)}${suffix ? ` ${suffix}` : ""}`;
}

function text(row: Record<string, unknown>, ...keys: string[]): string {
  for (const key of keys) {
    const value = row[key];
    if (value !== undefined && value !== null && String(value).trim()) return String(value);
  }
  return "";
}

function normalizeContractor(value: string): string {
  return value.trim().toLowerCase();
}

function collectContractorOptions(collections: ProjectCollections): string[] {
  return [...collections.gis_node, ...collections.gis_route, ...collections.material_handover]
    .map((row) => text(row, "contractor", "contractorName"))
    .filter(Boolean)
    .filter((value, index, array) => array.indexOf(value) === index)
    .sort((left, right) => left.localeCompare(right, "vi"));
}

function filterCollectionsForMember(
  collections: ProjectCollections,
  member: ProjectMemberRow | null,
  isAdmin: boolean
): ProjectCollections {
  if (isAdmin || !member || member.contractorScope !== "SCOPED" || !member.allowedContractors.length) {
    return collections;
  }

  const allowedContractors = new Set(member.allowedContractors.map(normalizeContractor));
  const allowedNodes = collections.gis_node.filter((row) => allowedContractors.has(normalizeContractor(text(row, "contractor", "contractorName"))));
  const allowedRoutes = collections.gis_route.filter((row) => allowedContractors.has(normalizeContractor(text(row, "contractor", "contractorName"))));
  const allowedNodeCodes = new Set(allowedNodes.map((row) => text(row, "code", "nodeCode", "name")));
  const allowedRouteCodes = new Set(allowedRoutes.map((row) => text(row, "code", "routeCode", "name")));

  return {
    ...collections,
    gis_node: allowedNodes,
    gis_route: allowedRoutes,
    work_volume_progress: collections.work_volume_progress.filter((row) => allowedNodeCodes.has(text(row, "nodeCode", "code"))),
    work_plan: collections.work_plan.filter((row) => {
      const nodeCode = text(row, "nodeCode");
      const routeCode = text(row, "routeCode");
      return (!nodeCode || allowedNodeCodes.has(nodeCode)) && (!routeCode || allowedRouteCodes.has(routeCode));
    }),
    material_handover: collections.material_handover.filter((row) => allowedContractors.has(normalizeContractor(text(row, "contractor", "contractorName"))))
  };
}

export default function HomePage() {
  const [user, setUser] = useState<FirebaseUser | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);
  const [authReady, setAuthReady] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>("signIn");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authNotice, setAuthNotice] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [projects, setProjects] = useState<ProjectDoc[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [manualProjectId, setManualProjectId] = useState("");
  const [projectDraft, setProjectDraft] = useState<ProjectDraft>(emptyProjectDraft);
  const [projectCreateState, setProjectCreateState] = useState("");
  const [projectCreateBusy, setProjectCreateBusy] = useState(false);
  const [project, setProject] = useState<ProjectDoc | null>(null);
  const [collections, setCollections] = useState<ProjectCollections>(() => emptyProjectCollections());
  const [currentMember, setCurrentMember] = useState<ProjectMemberRow | null>(null);
  const [usersDirectory, setUsersDirectory] = useState<UserProfileRow[]>([]);
  const [projectMembers, setProjectMembers] = useState<ProjectMemberRow[]>([]);
  const [selectedManagedUid, setSelectedManagedUid] = useState("");
  const [memberDraft, setMemberDraft] = useState<ProjectMemberRow | null>(null);
  const [memberSearch, setMemberSearch] = useState("");
  const [memberWriteState, setMemberWriteState] = useState("");
  const [memberWriteBusy, setMemberWriteBusy] = useState(false);
  const [accessError, setAccessError] = useState("");
  const [taskDraft, setTaskDraft] = useState(emptyTask);
  const [dailyLogDraft, setDailyLogDraft] = useState(emptyDailyLog);
  const [writeState, setWriteState] = useState("");
  const [writeBusy, setWriteBusy] = useState(false);
  const [activeTab, setActiveTab] = useState<"overview" | "tasks" | "media" | "admin">("overview");

  useEffect(() => {
    return observeAuth((nextUser) => {
      if (nextUser && !nextUser.emailVerified) {
        setUser(null);
        setAuthError("Tài khoản chưa kích hoạt. Vui lòng kiểm tra email xác thực trước khi đăng nhập.");
        void signOutCurrentUser();
        setAuthReady(true);
        return;
      }
      setUser(nextUser);
      setAuthReady(true);
      if (!nextUser) {
        setIsAdmin(false);
        setProjects([]);
        setSelectedProjectId("");
        setProject(null);
        setCollections(emptyProjectCollections());
        setCurrentMember(null);
        setUsersDirectory([]);
        setProjectMembers([]);
        setSelectedManagedUid("");
        setMemberDraft(null);
      }
    });
  }, []);

  useEffect(() => {
    let isCancelled = false;
    setIsAdmin(false);
    if (!user) return;
    getFirebaseUserAdminClaim(user)
      .then((admin) => {
        if (!isCancelled) setIsAdmin(admin);
      })
      .catch((error) => {
        console.warn("Failed to read Firebase token claims:", error);
        if (!isCancelled) setIsAdmin(false);
      });
    return () => {
      isCancelled = true;
    };
  }, [user]);

  useEffect(() => {
    if (!db || !user) return;
    void upsertUserProfile(db, {
      uid: user.uid,
      email: user.email,
      displayName: user.displayName,
      emailVerified: user.emailVerified
    }).catch((error) => {
      console.warn("Failed to upsert user profile:", error);
    });
  }, [user]);

  useEffect(() => {
    if (!db || !user) return;
    const firestore = db;
    setAccessError("");
    const unsubscribe = subscribeProjects(
      firestore,
      (rows) => {
        setProjects(rows);
        setAccessError("");
        setSelectedProjectId((current) => current || rows[0]?.id || "");
      },
      (error) => {
        setProjects([]);
        console.warn("Failed to subscribe projects list:", error);
      }
    );
    return unsubscribe;
  }, [user]);

  useEffect(() => {
    if (!db || !user || !isAdmin) {
      setUsersDirectory([]);
      return;
    }
    return subscribeUsersDirectory(
      db,
      (rows) => setUsersDirectory(rows),
      (error) => console.warn("Failed to subscribe users directory:", error)
    );
  }, [user, isAdmin]);

  useEffect(() => {
    if (!db || !user || !selectedProjectId) return;
    const firestore = db;
    setProject(null);
    setAccessError("");
    const unsubscribe = subscribeProjectDocument(
      firestore,
      selectedProjectId,
      (nextProject) => {
        setProject(nextProject);
        if (!nextProject) {
          setAccessError("Không tìm thấy dự án hoặc dự án đã bị xóa.");
        } else {
          setAccessError("");
        }
      },
      (error) => setAccessError(errorMessage(error))
    );
    return unsubscribe;
  }, [selectedProjectId, user]);

  useEffect(() => {
    if (!db || !user || !selectedProjectId) {
      setCurrentMember(null);
      return;
    }
    return subscribeCurrentProjectMember(
      db,
      selectedProjectId,
      user.uid,
      (row) => setCurrentMember(row),
      (error) => console.warn("Failed to subscribe current member:", error)
    );
  }, [selectedProjectId, user]);

  useEffect(() => {
    if (!db || !selectedProjectId || !isAdmin) {
      setProjectMembers([]);
      return;
    }
    return subscribeProjectMembers(
      db,
      selectedProjectId,
      (rows) => setProjectMembers(rows),
      (error) => console.warn("Failed to subscribe project members:", error)
    );
  }, [selectedProjectId, isAdmin]);

  useEffect(() => {
    if (!selectedManagedUid) {
      setMemberDraft(null);
      return;
    }
    const selectedUser = usersDirectory.find((item) => item.uid === selectedManagedUid);
    const existingMember = projectMembers.find((item) => item.uid === selectedManagedUid);
    if (!selectedUser && !existingMember) {
      setMemberDraft(null);
      return;
    }
    setMemberDraft({
      uid: selectedManagedUid,
      email: existingMember?.email ?? selectedUser?.email ?? "",
      displayName: existingMember?.displayName ?? selectedUser?.displayName ?? null,
      role: "MEMBER",
      isActive: existingMember?.isActive ?? true,
      contractorScope: existingMember?.contractorScope ?? "ALL",
      allowedContractors: existingMember?.allowedContractors ?? [],
      grantedByUid: existingMember?.grantedByUid ?? user?.uid ?? "",
      grantedAtEpochMs: existingMember?.grantedAtEpochMs ?? Date.now(),
      updatedAtEpochMs: existingMember?.updatedAtEpochMs ?? Date.now()
    });
  }, [selectedManagedUid, usersDirectory, projectMembers, user]);

  useEffect(() => {
    if (!db || !user || !selectedProjectId || accessError) {
      setCollections(emptyProjectCollections());
      return;
    }
    const firestore = db;
    setCollections(emptyProjectCollections());
    const unsubscribes = syncTables.map((tableName) =>
      subscribeProjectTable(
        firestore,
        selectedProjectId,
        tableName,
        (updatedTable, rows) => {
          setCollections((current) => ({ ...current, [updatedTable]: rows }));
        },
        () => undefined
      )
    );
    return () => unsubscribes.forEach((unsubscribe) => unsubscribe());
  }, [selectedProjectId, user, accessError]);

  const visibleCollections = useMemo(
    () => filterCollectionsForMember(collections, currentMember, isAdmin),
    [collections, currentMember, isAdmin]
  );

  const contractorOptions = useMemo(
    () => collectContractorOptions(collections),
    [collections]
  );

  const filteredUsers = useMemo(() => {
    const query = memberSearch.trim().toLowerCase();
    if (!query) return usersDirectory;
    return usersDirectory.filter((row) =>
      row.email.toLowerCase().includes(query) ||
      (row.displayName ?? "").toLowerCase().includes(query)
    );
  }, [usersDirectory, memberSearch]);

  const stats = useMemo(() => {
    const latest = syncTables
      .flatMap((tableName) => visibleCollections[tableName].map((row) => Number(row.updatedAtEpochMs ?? row.createdAtEpochMs ?? 0)))
      .filter((value) => Number.isFinite(value) && value > 0)
      .sort((left, right) => right - left)[0];
    const plannedQty = visibleCollections.work_volume_progress.reduce((sum, row) => sum + Number(row.plannedQty ?? row.quantity ?? 0), 0);
    const actualQty = visibleCollections.work_volume_progress.reduce((sum, row) => sum + Number(row.actualQty ?? row.completedQty ?? 0), 0);
    const openTasks = visibleCollections.task.filter((task) => String(task.status ?? "TODO") !== "DONE").length;
    return {
      latest,
      plannedQty,
      actualQty,
      openTasks,
      materialPercent: plannedQty > 0 ? Math.min(100, (actualQty / plannedQty) * 100) : 0
    };
  }, [visibleCollections]);

  async function handleAuthSubmit() {
    if (authMode === "register" && password !== confirmPassword) {
      setAuthError("Mật khẩu xác nhận không khớp.");
      return;
    }
    setAuthBusy(true);
    setAuthError("");
    setAuthNotice("");
    try {
      if (authMode === "register") {
        const newUser = await registerWithEmail(email, password);
        await sendVerificationEmail(newUser);
        await signOutCurrentUser();
        setAuthNotice("Đã gửi email kích hoạt tài khoản. Vui lòng mở email và bấm link xác thực trước khi đăng nhập.");
        setAuthMode("signIn");
      } else {
        const signedInUser = await signInWithEmail(email, password);
        if (!signedInUser.emailVerified) {
          await sendVerificationEmail(signedInUser);
          await signOutCurrentUser();
          setAuthError("Tài khoản chưa kích hoạt. Hệ thống đã gửi lại email xác thực, vui lòng kiểm tra hộp thư.");
          return;
        }
      }
      setPassword("");
      setConfirmPassword("");
    } catch (error) {
      setAuthError(errorMessage(error));
    } finally {
      setAuthBusy(false);
    }
  }

  async function handleSignOut() {
    await signOutCurrentUser();
  }

  function openManualProject() {
    const value = manualProjectId.trim();
    if (value) {
      setSelectedProjectId(value);
      setAccessError("");
    }
  }

  async function handleCreateProject() {
    if (!db || !user || !isAdmin || !projectDraft.name.trim()) return;
    const firestore = db;
    setProjectCreateBusy(true);
    setProjectCreateState("Đang tạo dự án...");
    try {
      const createdProject = await createProjectDocument(
        firestore,
        {
          uid: user.uid,
          email: user.email,
          displayName: user.displayName
        },
        projectDraft
      );
      setProjectDraft(emptyProjectDraft);
      setSelectedProjectId(createdProject.id);
      setManualProjectId("");
      setAccessError("");
      setProjectCreateState(`Đã tạo dự án ${createdProject.name}.`);
    } catch (error) {
      setProjectCreateState(errorMessage(error));
    } finally {
      setProjectCreateBusy(false);
    }
  }

  async function handleSaveMember() {
    if (!db || !user || !isAdmin || !selectedProjectId || !memberDraft) return;
    const firestore = db;
    setMemberWriteBusy(true);
    setMemberWriteState("Dang luu phan quyen...");
    try {
      await saveProjectMember(firestore, selectedProjectId, user.uid, memberDraft);
      setMemberWriteState(`Da cap quyen cho ${memberDraft.email}.`);
    } catch (error) {
      setMemberWriteState(errorMessage(error));
    } finally {
      setMemberWriteBusy(false);
    }
  }

  async function handleDeleteMember(uid: string) {
    if (!db || !isAdmin || !selectedProjectId) return;
    setMemberWriteBusy(true);
    setMemberWriteState("Dang thu hoi quyen...");
    try {
      await deleteProjectMemberRecord(db, selectedProjectId, uid);
      if (selectedManagedUid === uid) {
        setSelectedManagedUid("");
      }
      setMemberWriteState("Da thu hoi quyen project.");
    } catch (error) {
      setMemberWriteState(errorMessage(error));
    } finally {
      setMemberWriteBusy(false);
    }
  }

  async function handleCreateTask() {
    if (!db || !selectedProjectId || !taskDraft.title.trim()) return;
    const firestore = db;
    setWriteBusy(true);
    setWriteState("Đang tạo công việc...");
    try {
      await createTaskDocument(firestore, selectedProjectId, taskDraft.title, taskDraft.description);
      setTaskDraft(emptyTask);
      setWriteState("Đã ghi công việc lên Firestore thành công.");
    } catch (error) {
      setWriteState(errorMessage(error));
    } finally {
      setWriteBusy(false);
    }
  }

  async function handleCreateDailyLog() {
    if (!db || !selectedProjectId || !dailyLogDraft.workItem.trim()) return;
    const firestore = db;
    setWriteBusy(true);
    setWriteState("Đang tạo nhật ký thi công...");
    try {
      await createDailyLogDocument(firestore, selectedProjectId, dailyLogDraft);
      setDailyLogDraft(emptyDailyLog);
      setWriteState("Đã ghi nhật ký lên Firestore thành công.");
    } catch (error) {
      setWriteState(errorMessage(error));
    } finally {
      setWriteBusy(false);
    }
  }

  async function handleTaskStatus(task: Record<string, unknown>, status: TaskRow["status"]) {
    if (!db || !selectedProjectId) return;
    const firestore = db;
    setWriteState("Đang cập nhật trạng thái công việc...");
    try {
      await updateTaskStatusDocument(firestore, selectedProjectId, task, status);
      setWriteState("Đã cập nhật trạng thái công việc thành công.");
    } catch (error) {
      setWriteState(errorMessage(error));
    }
  }

  if (!firebaseReady) {
    return (
      <main className="shell centered">
        <section className="auth-card">
          <p className="eyebrow">MapSupervision Sync</p>
          <h1>Thiếu cấu hình Firebase</h1>
          <p className="muted">
            Vui lòng cấu hình các biến môi trường <code>NEXT_PUBLIC_FIREBASE_*</code> trong file <code>webapp/.env.local</code> và khởi động lại ứng dụng.
          </p>
        </section>
      </main>
    );
  }

  if (!authReady) {
    return (
      <main className="shell centered">
        <section className="auth-card" style={{ textAlign: "center" }}>
          <p className="eyebrow loading-pulse">Đang kết nối</p>
          <h1>Đang xác thực tài khoản...</h1>
        </section>
      </main>
    );
  }

  if (!user) {
    return (
      <main className="shell centered">
        <section className="auth-card">
          <p className="eyebrow">Firebase Auth</p>
          <h1>{authMode === "register" ? "Đăng ký tài khoản" : "Đăng nhập hệ thống"}</h1>
          <p className="muted">
            {authMode === "register"
              ? "Tạo tài khoản bằng email và mật khẩu. Sau khi đăng ký, tài khoản vẫn cần được admin phân quyền trong Firestore để đọc dữ liệu dự án."
              : "Sử dụng email và mật khẩu được cấp để truy cập hệ thống giám sát. Tài khoản cần được phân quyền trong Firestore để đọc dữ liệu dự án."}
          </p>
          <label>
            Địa chỉ Email
            <input
              value={email}
              type="email"
              placeholder="name@company.com"
              autoComplete="email"
              disabled={authBusy}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label>
            Mật khẩu
            <input
              value={password}
              type="password"
              placeholder="••••••••"
              autoComplete={authMode === "register" ? "new-password" : "current-password"}
              disabled={authBusy}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {authMode === "register" ? (
            <label>
              Xác nhận mật khẩu
              <input
                value={confirmPassword}
                type="password"
                placeholder="••••••••"
                autoComplete="new-password"
                disabled={authBusy}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </label>
          ) : null}
          {authError ? <p className="error">{authError}</p> : null}
          {authNotice ? <p className="success">{authNotice}</p> : null}
          <button
            className="primary-button"
            type="button"
            disabled={authBusy || !email.trim() || !password || (authMode === "register" && !confirmPassword)}
            onClick={() => void handleAuthSubmit()}
          >
            {authBusy ? "Đang xử lý..." : authMode === "register" ? "Tạo tài khoản" : "Đăng nhập"}
          </button>
          <button
            className="secondary-button"
            type="button"
            disabled={authBusy}
            onClick={() => {
              setAuthMode((current) => (current === "register" ? "signIn" : "register"));
              setAuthError("");
              setAuthNotice("");
              setConfirmPassword("");
            }}
          >
            {authMode === "register" ? "Đã có tài khoản? Đăng nhập" : "Chưa có tài khoản? Đăng ký"}
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Android x Firebase Realtime Bridge</p>
          <h1>MapSupervision Dashboard</h1>
          <p className="muted">
            Bảng điều khiển đồng bộ hai chiều. Đọc dữ liệu thực địa từ thiết bị và chỉ định nhiệm vụ/nhật ký ngược về Firestore.
          </p>
        </div>
        <div className="account-panel">
          <span>{user.email ?? "Tài khoản Firebase"}</span>
          <code>{user.uid}</code>
          <button className="ghost-button" type="button" onClick={() => void handleSignOut()}>
            Đăng xuất
          </button>
        </div>
      </header>

      <section className="project-strip">
        <label>
          Chọn dự án hoạt động
          <select value={selectedProjectId} onChange={(event) => { setSelectedProjectId(event.target.value); setAccessError(""); }}>
            <option value="">Chưa chọn dự án</option>
            {projects.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}{item.projectCode ? ` - ${item.projectCode}` : ""}
              </option>
            ))}
          </select>
          <span className="field-hint">{isAdmin ? "Admin" : "User"} - {projects.length} du an da tai</span>
        </label>
        <label>
          Mở nhanh bằng Project ID
          <div className="inline-control">
            <input
              value={manualProjectId}
              placeholder="Nhập mã dự án..."
              onChange={(event) => setManualProjectId(event.target.value)}
            />
            <button type="button" className="secondary-button" onClick={openManualProject}>
              Mở
            </button>
          </div>
        </label>
        <div className="status-box">
          <span className={accessError ? "status-dot danger" : selectedProjectId ? "status-dot ok" : "status-dot"} />
          <div>
            <strong>{(project?.name ?? selectedProjectId) || "Chưa chọn dự án"}</strong>
            <p>{accessError || (selectedProjectId ? `Cập nhật: ${formatDateTime(stats.latest)}` : "Đang chờ chọn dự án...")}</p>
          </div>
        </div>
      </section>

      {/* Tab selector */}
      <nav className="tab-container">
        <button
          className={`tab-btn ${activeTab === "overview" ? "active" : ""}`}
          type="button"
          onClick={() => setActiveTab("overview")}
        >
          Sơ đồ & Trạng thái
        </button>
        <button
          className={`tab-btn ${activeTab === "tasks" ? "active" : ""}`}
          type="button"
          onClick={() => setActiveTab("tasks")}
        >
          Nhiệm vụ & Nhật ký
        </button>
        <button
          className={`tab-btn ${activeTab === "media" ? "active" : ""}`}
          type="button"
          onClick={() => setActiveTab("media")}
        >
          Hình ảnh & Ghi chú
        </button>
        {isAdmin ? (
          <button
            className={`tab-btn ${activeTab === "admin" ? "active" : ""}`}
            type="button"
            onClick={() => setActiveTab("admin")}
          >
            Quản trị & Cấp quyền
          </button>
        ) : null}
      </nav>

      {/* Tab Content: Overview */}
      {activeTab === "overview" && (
        <>
          {accessError ? (
            <section className="notice danger" style={{ padding: "32px", display: "grid", gap: "16px" }}>
              <h2 style={{ margin: 0, fontSize: "16px", fontWeight: "800", color: "var(--danger)" }}>
                YÊU CẦU CẤP QUYỀN TRUY CẬP DỰ ÁN
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                Tài khoản của bạn chưa được cấp quyền đọc dữ liệu cho mã dự án <code>{selectedProjectId}</code>. 
                Vui lòng liên hệ Admin và gửi thông tin tài khoản dưới đây để được cấp quyền truy cập.
              </p>
              <div style={{ display: "grid", gap: "12px", background: "var(--surface-soft)", padding: "16px", border: "1px solid var(--line)", borderRadius: "var(--radius)" }}>
                <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: "8px", fontSize: "13px" }}>
                  <span style={{ color: "var(--muted)", fontWeight: "bold" }}>EMAIL:</span>
                  <strong style={{ fontFamily: "var(--font-mono)", color: "var(--ink)" }}>{user.email}</strong>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: "8px", fontSize: "13px" }}>
                  <span style={{ color: "var(--muted)", fontWeight: "bold" }}>UID USER:</span>
                  <strong style={{ fontFamily: "var(--font-mono)", color: "var(--accent)" }}>{user.uid}</strong>
                </div>
                <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: "8px", fontSize: "13px" }}>
                  <span style={{ color: "var(--muted)", fontWeight: "bold" }}>DỰ ÁN:</span>
                  <strong style={{ fontFamily: "var(--font-mono)", color: "var(--ink)" }}>{selectedProjectId}</strong>
                </div>
              </div>
              <div className="muted" style={{ fontSize: "13px" }}>
                Admin cần tạo document Firestore tại đường dẫn sau để cho phép bạn truy cập:
                <code style={{ display: "block", marginTop: "8px", padding: "10px", background: "var(--bg)", border: "1px solid var(--line)", borderRadius: "var(--radius)", color: "var(--ink)", overflowX: "auto" }}>
                  projects/{selectedProjectId}/projectMembers/{user.uid}
                </code>
              </div>
            </section>
          ) : !selectedProjectId ? (
            <section className="notice" style={{ padding: "32px", textAlign: "center" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>CHƯA CHỌN DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án từ danh sách phía trên hoặc nhập mã dự án để mở bảng dữ liệu giám sát realtime.
              </p>
            </section>
          ) : (
            <>
              {/* Stats Summary cards */}
              <section className="stat-grid">
                <StatCard label="Điểm thiết kế (Node)" value={visibleCollections.gis_node.length} detail={`${visibleCollections.gis_route.length} Tuyến cáp`} />
                <StatCard label="Nhiệm vụ đang mở" value={stats.openTasks} detail={`${visibleCollections.task.length} Nhiệm vụ tổng`} />
                <StatCard label="Nhật ký tuần" value={visibleCollections.daily_log.length} detail={`${visibleCollections.note.length} Ghi chú nhanh`} />
                <StatCard label="Hình ảnh thực địa" value={visibleCollections.site_photos.length} detail={`${visibleCollections.material_handover.length} Phiếu bàn giao`} />
                <StatCard label="Tiến độ thi công" value={`${stats.materialPercent.toFixed(1)}%`} detail={`${formatNumber(stats.actualQty)} / ${formatNumber(stats.plannedQty)}`} progress={stats.materialPercent} />
              </section>

              {/* MapLibre map and realtime table list */}
              <section className="main-grid">
                <Panel title="Sơ đồ điểm nút & tuyến thi công" subtitle="Bản đồ nền và dữ liệu GIS đồng bộ realtime từ Android">
                  <div className="map-frame">
                    <GisWebMap nodes={visibleCollections.gis_node} routes={visibleCollections.gis_route} />
                  </div>
                </Panel>
              </section>
            </>
          )}
        </>
      )}

      {/* Tab Content: Tasks */}
      {activeTab === "tasks" && (
        <>
          {accessError ? (
            <section className="notice danger" style={{ padding: "32px", display: "grid", gap: "16px" }}>
              <h2 style={{ margin: 0, fontSize: "16px", fontWeight: "800", color: "var(--danger)" }}>
                YÊU CẦU CẤP QUYỀN TRUY CẬP DỰ ÁN
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng liên hệ Admin để cấp quyền truy cập dự án <code>{selectedProjectId}</code>.
              </p>
            </section>
          ) : !selectedProjectId ? (
            <section className="notice" style={{ padding: "32px", textAlign: "center" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>CHƯA CHỌN DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án từ danh sách phía trên hoặc nhập mã dự án để thực hiện nhiệm vụ.
              </p>
            </section>
          ) : (
            <>
              {/* Forms for writing back tasks and logs */}
              <section className="write-grid">
                <Panel title="Chỉ định nhiệm vụ mới" subtitle={writeState && writeState.includes("task") ? writeState : undefined}>
                  <TextInput label="Tiêu đề công việc" value={taskDraft.title} onChange={(value) => setTaskDraft((current) => ({ ...current, title: value }))} />
                  <TextArea label="Mô tả chi tiết" value={taskDraft.description} onChange={(value) => setTaskDraft((current) => ({ ...current, description: value }))} />
                  <button className="primary-button" type="button" disabled={writeBusy || !selectedProjectId || !taskDraft.title.trim()} onClick={() => void handleCreateTask()}>
                    Ghi công việc lên Firebase
                  </button>
                </Panel>

                <Panel title="Tạo nhật ký thi công" subtitle={writeState && writeState.includes("nhật ký") ? writeState : undefined}>
                  <TextInput label="Hạng mục công việc" value={dailyLogDraft.workItem} onChange={(value) => setDailyLogDraft((current) => ({ ...current, workItem: value }))} />
                  <div className="three-cols">
                    <TextInput label="Nhân công (người)" value={dailyLogDraft.manpower} onChange={(value) => setDailyLogDraft((current) => ({ ...current, manpower: value }))} />
                    <TextInput label="Khối lượng" value={dailyLogDraft.volume} onChange={(value) => setDailyLogDraft((current) => ({ ...current, volume: value }))} />
                    <TextInput label="Đơn vị tính" value={dailyLogDraft.unit} onChange={(value) => setDailyLogDraft((current) => ({ ...current, unit: value }))} />
                  </div>
                  <TextInput label="Phân nhóm hạng mục" value={dailyLogDraft.categoryName} onChange={(value) => setDailyLogDraft((current) => ({ ...current, categoryName: value }))} />
                  <TextInput label="Điều kiện thời tiết" value={dailyLogDraft.weather} onChange={(value) => setDailyLogDraft((current) => ({ ...current, weather: value }))} />
                  <TextArea label="Ghi chú thi công" value={dailyLogDraft.note} onChange={(value) => setDailyLogDraft((current) => ({ ...current, note: value }))} />
                  <button className="primary-button" type="button" disabled={writeBusy || !selectedProjectId || !dailyLogDraft.workItem.trim()} onClick={() => void handleCreateDailyLog()}>
                    Ghi nhật ký lên Firebase
                  </button>
                </Panel>
              </section>

              {/* Realtime Lists */}
              <section className="lists-grid">
                <Panel title="Danh sách công việc">
                  <List
                    rows={visibleCollections.task}
                    empty="Chưa có công việc nào được tạo."
                    render={(task) => (
                      <>
                        <strong>{text(task, "title") || "Công việc chưa đặt tên"}</strong>
                        <span>{text(task, "description") || "Không có mô tả chi tiết."}</span>
                        <div className="row-actions">
                          {(["TODO", "IN_PROGRESS", "DONE"] as const).map((status) => (
                            <button key={status} className={String(task.status ?? "TODO") === status ? "tiny-button active" : "tiny-button"} type="button" onClick={() => void handleTaskStatus(task, status)}>
                              {status}
                            </button>
                          ))}
                        </div>
                      </>
                    )}
                  />
                </Panel>
                <Panel title="Kế hoạch thi công">
                  <List
                    rows={visibleCollections.work_plan}
                    empty="Chưa có dữ liệu kế hoạch."
                    render={(plan) => (
                      <>
                        <strong>{text(plan, "title", "workName", "name") || "Kế hoạch thi công"}</strong>
                        <span>Vị trí: {text(plan, "nodeCode", "routeCode") || "Không xác định"} · Khối lượng: {formatNumber(plan.quantity, text(plan, "unit"))}</span>
                      </>
                    )}
                  />
                </Panel>
                <Panel title="Nhật ký thực địa">
                  <List
                    rows={visibleCollections.daily_log}
                    empty="Chưa ghi nhận nhật ký nào."
                    render={(log) => (
                      <>
                        <strong>{text(log, "workItem") || "Nhật ký thi công"}</strong>
                        <span>Nhân công: {formatNumber(log.manpower, "người")} · {text(log, "categoryName")} : {formatNumber(log.volume, text(log, "unit"))}</span>
                        <small>Thời gian: {formatDateTime(log.updatedAtEpochMs ?? log.createdAtEpochMs)}</small>
                      </>
                    )}
                  />
                </Panel>
                <Panel title="Vật tư & khối lượng">
                  <List
                    rows={[...visibleCollections.work_volume_progress, ...visibleCollections.material_declaration, ...visibleCollections.material_handover]}
                    empty="Chưa có dữ liệu kê khai vật tư."
                    render={(material) => (
                      <>
                        <strong>{text(material, "materialName", "workName", "name") || "Kê khai vật tư"}</strong>
                        <span>Vị trí: {text(material, "nodeCode") || "Không xác định"} · Số lượng: {formatNumber(material.quantity ?? material.actualQty, text(material, "unit"))}</span>
                      </>
                    )}
                  />
                </Panel>
              </section>
            </>
          )}
        </>
      )}

      {/* Tab Content: Media */}
      {activeTab === "media" && (
        <>
          {accessError ? (
            <section className="notice danger" style={{ padding: "32px", display: "grid", gap: "16px" }}>
              <h2 style={{ margin: 0, fontSize: "16px", fontWeight: "800", color: "var(--danger)" }}>
                YÊU CẦU CẤP QUYỀN TRUY CẬP DỰ ÁN
              </h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng liên hệ Admin để cấp quyền truy cập dự án <code>{selectedProjectId}</code>.
              </p>
            </section>
          ) : !selectedProjectId ? (
            <section className="notice" style={{ padding: "32px", textAlign: "center" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>CHƯA CHỌN DỰ ÁN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án từ danh sách phía trên hoặc nhập mã dự án để xem thư viện hình ảnh thực địa.
              </p>
            </section>
          ) : (
            <section className="media-grid">
              <Panel title="Thư viện hình ảnh thực địa" subtitle="Đồng bộ từ Google Drive nếu thiết bị đã tải lên">
                {visibleCollections.site_photos.length ? (
                  <div className="photo-grid">
                    {visibleCollections.site_photos.slice(0, 9).map((rawPhoto, index) => {
                      const photo = rawPhoto as SitePhotoRow;
                      const url = text(photo, "remoteUrl", "url");
                      const isRemote = url && (url.startsWith("http://") || url.startsWith("https://"));
                      return (
                        <a key={text(photo, "id") || index} href={isRemote ? url : undefined} target={isRemote ? "_blank" : undefined} rel="noreferrer" className="photo-tile">
                          {isRemote ? <img src={url} alt={text(photo, "objectCode", "caption") || "Ảnh hiện trường"} /> : <div className="photo-placeholder" />}
                          <strong>{text(photo, "objectCode", "nodeCode", "routeCode") || "Ảnh chưa phân loại"}</strong>
                          <span>Kỹ sư: {text(photo, "engineer", "capturedBy") || "Không rõ"} · {formatDateTime(photo.capturedAtEpochMs ?? photo.updatedAtEpochMs)}</span>
                          <span>Sync: {text(photo, "syncStatus") || (isRemote ? "DONE" : "PENDING")}</span>
                        </a>
                      );
                    })}
                  </div>
                ) : (
                  <div className="empty-state">Chưa có hình ảnh nào được tải lên từ thực địa.</div>
                )}
              </Panel>
              <Panel title="Ghi chú nhanh">
                <List
                  rows={visibleCollections.note}
                  empty="Chưa có ghi chú nhanh nào."
                  render={(note) => (
                    <>
                      <strong>{text(note, "title", "objectCode") || "Ghi chú"}</strong>
                      <span>{text(note, "content", "note", "description")}</span>
                    </>
                  )}
                />
              </Panel>
            </section>
          )}
        </>
      )}

      {/* Tab Content: Admin */}
      {activeTab === "admin" && isAdmin && (
        <>
          <section className="project-create-panel">
            <label>
              Tên dự án mới
              <input
                value={projectDraft.name}
                placeholder="Nhập tên dự án..."
                disabled={projectCreateBusy}
                onChange={(event) => setProjectDraft((current) => ({ ...current, name: event.target.value }))}
              />
            </label>
            <label>
              Mã dự án
              <input
                value={projectDraft.projectCode ?? ""}
                placeholder="Tùy chọn"
                disabled={projectCreateBusy}
                onChange={(event) => setProjectDraft((current) => ({ ...current, projectCode: event.target.value }))}
              />
            </label>
            <button
              className="primary-button"
              type="button"
              disabled={projectCreateBusy || !projectDraft.name.trim()}
              onClick={() => void handleCreateProject()}
            >
              {projectCreateBusy ? "Đang tạo..." : "Tạo dự án mới"}
            </button>
            {projectCreateState ? (
              <p className={projectCreateState.startsWith("Đã") ? "success" : "error"}>
                {projectCreateState}
              </p>
            ) : (
              <p className="muted">Chỉ tài khoản admin mới có thể tạo dự án trên Firestore.</p>
            )}
          </section>

          {selectedProjectId ? (
            <AdminAccessPanel
              users={filteredUsers}
              projectMembers={projectMembers}
              contractorOptions={contractorOptions}
              selectedManagedUid={selectedManagedUid}
              onSelectManagedUid={setSelectedManagedUid}
              memberDraft={memberDraft}
              onMemberDraftChange={setMemberDraft}
              memberSearch={memberSearch}
              onMemberSearchChange={setMemberSearch}
              memberWriteBusy={memberWriteBusy}
              memberWriteState={memberWriteState}
              onSaveMember={() => void handleSaveMember()}
              onDeleteMember={(uid) => void handleDeleteMember(uid)}
            />
          ) : (
            <section className="notice" style={{ padding: "32px", textAlign: "center", marginTop: "24px" }}>
              <h2 style={{ margin: "0 0 8px 0", fontSize: "16px", fontWeight: "800" }}>QUẢN LÝ THÀNH VIÊN</h2>
              <p className="muted" style={{ margin: 0 }}>
                Vui lòng chọn một dự án cụ thể ở phía trên để quản lý thành viên và phân quyền truy cập nhà thầu.
              </p>
            </section>
          )}
        </>
      )}
    </main>
  );
}

function StatCard({
  label,
  value,
  detail,
  progress
}: {
  label: string;
  value: string | number;
  detail: string;
  progress?: number;
}) {
  return (
    <section className="stat-card">
      <span>{label}</span>
      <strong>{value}</strong>
      {progress !== undefined && (
        <div className="progress-bar-container">
          <div className="progress-bar-fill" style={{ width: `${progress}%` }} />
        </div>
      )}
      <small>{detail}</small>
    </section>
  );
}

function Panel({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>{title}</h2>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
      {children}
    </section>
  );
}

function TextInput({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label style={{ display: "grid", gap: "6px" }}>
      {label}
      <input value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function TextArea({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label style={{ display: "grid", gap: "6px" }}>
      {label}
      <textarea value={value} rows={4} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}

function AdminAccessPanel({
  users,
  projectMembers,
  contractorOptions,
  selectedManagedUid,
  onSelectManagedUid,
  memberDraft,
  onMemberDraftChange,
  memberSearch,
  onMemberSearchChange,
  memberWriteBusy,
  memberWriteState,
  onSaveMember,
  onDeleteMember
}: {
  users: UserProfileRow[];
  projectMembers: ProjectMemberRow[];
  contractorOptions: string[];
  selectedManagedUid: string;
  onSelectManagedUid: (uid: string) => void;
  memberDraft: ProjectMemberRow | null;
  onMemberDraftChange: (member: ProjectMemberRow | null) => void;
  memberSearch: string;
  onMemberSearchChange: (value: string) => void;
  memberWriteBusy: boolean;
  memberWriteState: string;
  onSaveMember: () => void;
  onDeleteMember: (uid: string) => void;
}) {
  return (
    <section className="admin-grid">
      <Panel title="Nguoi dung da dang nhap" subtitle={`${users.length} tai khoan`}>
        <TextInput label="Tim theo email" value={memberSearch} onChange={onMemberSearchChange} />
        <div className="item-list compact-list">
          {users.slice(0, 12).map((row) => (
            <button
              key={row.uid}
              type="button"
              className={selectedManagedUid === row.uid ? "admin-list-item active" : "admin-list-item"}
              onClick={() => onSelectManagedUid(row.uid)}
            >
              <strong>{row.email}</strong>
              <span>{row.displayName || row.uid}</span>
              <small>{row.projectIds.length ? row.projectIds.join(", ") : "Chua co project"}</small>
            </button>
          ))}
          {!users.length ? <div className="empty-state">Chua co user nao dang nhap.</div> : null}
        </div>
      </Panel>

      <Panel title="Thanh vien project" subtitle={`${projectMembers.length} member`}>
        <div className="item-list compact-list">
          {projectMembers.map((member) => (
            <div key={member.uid} className="admin-list-item">
              <strong>{member.email}</strong>
              <span>{member.contractorScope} · {member.isActive ? "Active" : "Inactive"}</span>
              <small>{member.allowedContractors.length ? member.allowedContractors.join(", ") : "All contractors"}</small>
              <div className="row-actions">
                <button className="tiny-button" type="button" onClick={() => onSelectManagedUid(member.uid)}>
                  Chinh sua
                </button>
                <button className="tiny-button" type="button" onClick={() => onDeleteMember(member.uid)}>
                  Thu hoi
                </button>
              </div>
            </div>
          ))}
          {!projectMembers.length ? <div className="empty-state">Project chua co member nao.</div> : null}
        </div>
      </Panel>

      <Panel title="Cap quyen project" subtitle={memberWriteState || "Them user vao project va gan contractor scope"}>
        {memberDraft ? (
          <>
            <TextInput
              label="Email"
              value={memberDraft.email}
              onChange={(value) => onMemberDraftChange({ ...memberDraft, email: value })}
            />
            <TextInput
              label="Display name"
              value={memberDraft.displayName ?? ""}
              onChange={(value) => onMemberDraftChange({ ...memberDraft, displayName: value || null })}
            />
            <label>
              Trang thai
              <select
                value={memberDraft.isActive ? "ACTIVE" : "INACTIVE"}
                onChange={(event) => onMemberDraftChange({ ...memberDraft, isActive: event.target.value === "ACTIVE" })}
              >
                <option value="ACTIVE">ACTIVE</option>
                <option value="INACTIVE">INACTIVE</option>
              </select>
            </label>
            <label>
              Contractor scope
              <select
                value={memberDraft.contractorScope}
                onChange={(event) =>
                  onMemberDraftChange({
                    ...memberDraft,
                    contractorScope: event.target.value as ContractorScope,
                    allowedContractors: event.target.value === "SCOPED" ? memberDraft.allowedContractors : []
                  })
                }
              >
                <option value="ALL">ALL</option>
                <option value="SCOPED">SCOPED</option>
              </select>
            </label>
            {memberDraft.contractorScope === "SCOPED" ? (
              <div className="contractor-picker">
                {contractorOptions.map((contractor) => {
                  const checked = memberDraft.allowedContractors.includes(contractor);
                  return (
                    <label key={contractor} className="contractor-option">
                      <input
                        type="checkbox"
                        checked={checked}
                        onChange={(event) => {
                          const nextValues = event.target.checked
                             ? [...memberDraft.allowedContractors, contractor]
                             : memberDraft.allowedContractors.filter((value) => value !== contractor);
                          onMemberDraftChange({ ...memberDraft, allowedContractors: nextValues });
                        }}
                      />
                      <span>{contractor}</span>
                    </label>
                  );
                })}
                {!contractorOptions.length ? <div className="empty-state">Project chua co contractor de gan scope.</div> : null}
              </div>
            ) : null}
            <div className="row-actions">
              <button className="primary-button" type="button" disabled={memberWriteBusy || !memberDraft.email.trim()} onClick={onSaveMember}>
                Luu phan quyen
              </button>
              <button className="secondary-button" type="button" disabled={memberWriteBusy} onClick={() => onSelectManagedUid("")}>
                Bo chon
              </button>
            </div>
          </>
        ) : (
          <div className="empty-state">Chon mot user o cot ben trai hoac tu danh sach member de chinh sua.</div>
        )}
      </Panel>
    </section>
  );
}

function List<T extends Record<string, unknown>>({
  rows,
  render,
  empty
}: {
  rows: T[];
  render: (row: T) => React.ReactNode;
  empty: string;
}) {
  if (!rows.length) return <div className="empty-state">{empty}</div>;
  return (
    <div className="item-list">
      {rows.slice(0, 8).map((row, index) => (
        <article key={String(row.id ?? index)} className="list-item">
          {render(row)}
        </article>
      ))}
    </div>
  );
}
