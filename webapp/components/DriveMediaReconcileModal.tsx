"use client";

import React, { useState } from "react";
import { imageSourceUrl } from "@/lib/google-drive-image";
import type { DiscoveredDrivePhoto, DriveScanResult } from "@/lib/google-drive-media";

interface DriveMediaReconcileModalProps {
  isOpen: boolean;
  onClose: () => void;
  projectId: string;
  token?: string;
  onSuccess?: (reconciledCount: number) => void;
}

export function DriveMediaReconcileModal({
  isOpen,
  onClose,
  projectId,
  token,
  onSuccess
}: DriveMediaReconcileModalProps) {
  const [isScanning, setIsScanning] = useState(false);
  const [isReconciling, setIsReconciling] = useState(false);
  const [scanResult, setScanResult] = useState<DriveScanResult | null>(null);
  const [selectedPhotoIds, setSelectedPhotoIds] = useState<Set<string>>(new Set());
  const [statusMessage, setStatusMessage] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);

  if (!isOpen) return null;

  const handleScan = async () => {
    setIsScanning(true);
    setStatusMessage(null);
    try {
      const headers: Record<string, string> = {};
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      const res = await fetch(`/api/projects/${projectId}/media/scan`, {
        method: "POST",
        headers
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        throw new Error(json.error?.message || "Không thể quét Google Drive.");
      }
      const data: DriveScanResult = json.data;
      setScanResult(data);
      setSelectedPhotoIds(new Set(data.discoveredPhotos.map((p) => p.id)));
      if (data.discoveredPhotos.length === 0) {
        setStatusMessage({
          type: "info",
          text: `Đã quét ${data.totalDriveFiles} file trên Drive. Tất cả ảnh đã được đồng bộ đầy đủ!`
        });
      } else {
        setStatusMessage({
          type: "success",
          text: `Phát hiện ${data.discoveredPhotos.length} ảnh mới chưa có trong hệ thống!`
        });
      }
    } catch (err: any) {
      setStatusMessage({
        type: "error",
        text: err.message || "Lỗi khi quét Google Drive."
      });
    } finally {
      setIsScanning(false);
    }
  };

  const handleToggleSelectAll = () => {
    if (!scanResult) return;
    if (selectedPhotoIds.size === scanResult.discoveredPhotos.length) {
      setSelectedPhotoIds(new Set());
    } else {
      setSelectedPhotoIds(new Set(scanResult.discoveredPhotos.map((p) => p.id)));
    }
  };

  const handleTogglePhoto = (id: string) => {
    const next = new Set(selectedPhotoIds);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setSelectedPhotoIds(next);
  };

  const handleReconcile = async () => {
    if (!scanResult || selectedPhotoIds.size === 0) return;
    setIsReconciling(true);
    setStatusMessage(null);
    try {
      const photosToReconcile = scanResult.discoveredPhotos.filter((p) => selectedPhotoIds.has(p.id));
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      if (token) {
        headers["Authorization"] = `Bearer ${token}`;
      }
      const res = await fetch(`/api/projects/${projectId}/media/reconcile`, {
        method: "POST",
        headers,
        body: JSON.stringify({ photos: photosToReconcile })
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        throw new Error(json.error?.message || "Không thể bổ sung ảnh.");
      }

      setStatusMessage({
        type: "success",
        text: `Đã bổ sung thành công ${photosToReconcile.length} ảnh vào dự án và xuất bản Snapshot mới!`
      });

      // Update local state
      const remainingPhotos = scanResult.discoveredPhotos.filter((p) => !selectedPhotoIds.has(p.id));
      setScanResult({
        ...scanResult,
        matchedCount: scanResult.matchedCount + photosToReconcile.length,
        discoveredPhotos: remainingPhotos
      });
      setSelectedPhotoIds(new Set(remainingPhotos.map((p) => p.id)));

      if (onSuccess) {
        onSuccess(photosToReconcile.length);
      }
    } catch (err: any) {
      setStatusMessage({
        type: "error",
        text: err.message || "Lỗi khi bổ sung ảnh vào hệ thống."
      });
    } finally {
      setIsReconciling(false);
    }
  };

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(15, 23, 42, 0.75)",
        backdropFilter: "blur(6px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 9999,
        padding: "16px"
      }}
    >
      <div
        style={{
          backgroundColor: "#ffffff",
          borderRadius: "16px",
          width: "100%",
          maxWidth: "840px",
          maxHeight: "90vh",
          display: "flex",
          flexDirection: "column",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.25)",
          overflow: "hidden"
        }}
      >
        {/* Modal Header */}
        <div
          style={{
            padding: "20px 24px",
            borderBottom: "1px solid #e2e8f0",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between"
          }}
        >
          <div>
            <h3 style={{ fontSize: "1.25rem", fontWeight: "700", color: "#0f172a", margin: 0 }}>
              Quét & Đối soát ảnh Google Drive
            </h3>
            <p style={{ fontSize: "0.875rem", color: "#64748b", margin: "4px 0 0 0" }}>
              Tự động phát hiện các file ảnh thực tế trên Google Drive chưa được đồng bộ vào cơ sở dữ liệu.
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              cursor: "pointer",
              padding: "8px",
              borderRadius: "8px",
              color: "#64748b",
              fontSize: "1.25rem",
              lineHeight: 1
            }}
          >
            ✕
          </button>
        </div>

        {/* Modal Body */}
        <div style={{ padding: "24px", overflowY: "auto", flex: 1, display: "flex", flexDirection: "column", gap: "16px" }}>
          {/* Action & Stats Card */}
          <div
            style={{
              padding: "16px 20px",
              backgroundColor: "#f8fafc",
              border: "1px solid #e2e8f0",
              borderRadius: "12px",
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              flexWrap: "wrap",
              gap: "12px"
            }}
          >
            <div style={{ display: "flex", gap: "16px", flexWrap: "wrap" }}>
              <div>
                <div style={{ fontSize: "0.75rem", color: "#64748b", textTransform: "uppercase", fontWeight: "600" }}>
                  Tổng ảnh trên Drive
                </div>
                <div style={{ fontSize: "1.25rem", fontWeight: "700", color: "#0f172a" }}>
                  {scanResult ? scanResult.totalDriveFiles : "—"}
                </div>
              </div>
              <div style={{ borderLeft: "1px solid #cbd5e1", paddingLeft: "16px" }}>
                <div style={{ fontSize: "0.75rem", color: "#64748b", textTransform: "uppercase", fontWeight: "600" }}>
                  Đã khớp hệ thống
                </div>
                <div style={{ fontSize: "1.25rem", fontWeight: "700", color: "#16a34a" }}>
                  {scanResult ? scanResult.matchedCount : "—"}
                </div>
              </div>
              <div style={{ borderLeft: "1px solid #cbd5e1", paddingLeft: "16px" }}>
                <div style={{ fontSize: "0.75rem", color: "#64748b", textTransform: "uppercase", fontWeight: "600" }}>
                  Ảnh mới phát hiện
                </div>
                <div style={{ fontSize: "1.25rem", fontWeight: "700", color: "#ea580c" }}>
                  {scanResult ? scanResult.discoveredPhotos.length : "—"}
                </div>
              </div>
            </div>

            <button
              onClick={handleScan}
              disabled={isScanning || isReconciling}
              style={{
                padding: "10px 20px",
                backgroundColor: isScanning ? "#94a3b8" : "#2563eb",
                color: "#ffffff",
                fontWeight: "600",
                fontSize: "0.875rem",
                borderRadius: "8px",
                border: "none",
                cursor: isScanning ? "not-allowed" : "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: "8px",
                transition: "background 0.2s"
              }}
            >
              {isScanning ? "Đang quét Drive..." : "Bắt đầu quét Drive"}
            </button>
          </div>

          {/* Status Alert */}
          {statusMessage && (
            <div
              style={{
                padding: "12px 16px",
                borderRadius: "8px",
                fontSize: "0.875rem",
                fontWeight: "500",
                backgroundColor:
                  statusMessage.type === "success"
                    ? "#f0fdf4"
                    : statusMessage.type === "error"
                    ? "#fef2f2"
                    : "#eff6ff",
                color:
                  statusMessage.type === "success"
                    ? "#15803d"
                    : statusMessage.type === "error"
                    ? "#b91c1c"
                    : "#1d4ed8",
                border: `1px solid ${
                  statusMessage.type === "success"
                    ? "#bbf7d0"
                    : statusMessage.type === "error"
                    ? "#fecaca"
                    : "#bfdbfe"
                }`
              }}
            >
              {statusMessage.text}
            </div>
          )}

          {/* Discovered Photos Table */}
          {scanResult && scanResult.discoveredPhotos.length > 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <label style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", fontSize: "0.875rem", fontWeight: "600", color: "#334155" }}>
                  <input
                    type="checkbox"
                    checked={selectedPhotoIds.size === scanResult.discoveredPhotos.length}
                    onChange={handleToggleSelectAll}
                    style={{ width: "16px", height: "16px" }}
                  />
                  Chọn tất cả ({selectedPhotoIds.size}/{scanResult.discoveredPhotos.length} ảnh)
                </label>
              </div>

              <div
                style={{
                  border: "1px solid #e2e8f0",
                  borderRadius: "8px",
                  overflow: "hidden",
                  maxHeight: "360px",
                  overflowY: "auto"
                }}
              >
                <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.875rem" }}>
                  <thead style={{ backgroundColor: "#f1f5f9", position: "sticky", top: 0, zIndex: 1 }}>
                    <tr>
                      <th style={{ padding: "10px 12px", width: "40px" }}></th>
                      <th style={{ padding: "10px 12px", width: "70px" }}>Ảnh</th>
                      <th style={{ padding: "10px 12px" }}>Mã đối tượng</th>
                      <th style={{ padding: "10px 12px" }}>Thời gian</th>
                      <th style={{ padding: "10px 12px" }}>Địa chỉ / Ghi chú</th>
                    </tr>
                  </thead>
                  <tbody>
                    {scanResult.discoveredPhotos.map((photo) => {
                      const isSelected = selectedPhotoIds.has(photo.id);
                      const imageUrl = imageSourceUrl(photo.driveFileId, 120);
                      const timeStr = new Date(photo.capturedAtEpochMs).toLocaleString("vi-VN");

                      return (
                        <tr
                          key={photo.id}
                          style={{
                            borderBottom: "1px solid #f1f5f9",
                            backgroundColor: isSelected ? "#f8fafc" : "#ffffff"
                          }}
                        >
                          <td style={{ padding: "10px 12px", textAlign: "center" }}>
                            <input
                              type="checkbox"
                              checked={isSelected}
                              onChange={() => handleTogglePhoto(photo.id)}
                              style={{ width: "16px", height: "16px" }}
                            />
                          </td>
                          <td style={{ padding: "10px 12px" }}>
                            <div
                              style={{
                                width: "48px",
                                height: "48px",
                                borderRadius: "6px",
                                backgroundColor: "#e2e8f0",
                                overflow: "hidden",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center"
                              }}
                            >
                              {imageUrl ? (
                                <img
                                  src={imageUrl}
                                  alt={photo.name}
                                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                                  loading="lazy"
                                />
                              ) : (
                                <span style={{ fontSize: "0.75rem", color: "#94a3b8" }}>No Pic</span>
                              )}
                            </div>
                          </td>
                          <td style={{ padding: "10px 12px", fontWeight: "600", color: "#1e293b" }}>
                            <span
                              style={{
                                padding: "2px 8px",
                                borderRadius: "4px",
                                backgroundColor: "#e0f2fe",
                                color: "#0369a1",
                                fontSize: "0.75rem"
                              }}
                            >
                              {photo.objectCode}
                            </span>
                          </td>
                          <td style={{ padding: "10px 12px", color: "#475569" }}>
                            {timeStr}
                          </td>
                          <td style={{ padding: "10px 12px", color: "#475569", maxWidth: "250px" }}>
                            <div style={{ fontWeight: "500", color: "#1e293b", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                              {photo.address || "—"}
                            </div>
                            <div style={{ fontSize: "0.75rem", color: "#64748b", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                              {photo.captureNote || photo.name}
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div
          style={{
            padding: "16px 24px",
            borderTop: "1px solid #e2e8f0",
            backgroundColor: "#f8fafc",
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-end",
            gap: "12px"
          }}
        >
          <button
            onClick={onClose}
            disabled={isReconciling}
            style={{
              padding: "10px 18px",
              backgroundColor: "#ffffff",
              color: "#475569",
              border: "1px solid #cbd5e1",
              borderRadius: "8px",
              fontWeight: "600",
              fontSize: "0.875rem",
              cursor: "pointer"
            }}
          >
            Đóng
          </button>

          {scanResult && scanResult.discoveredPhotos.length > 0 && (
            <button
              onClick={handleReconcile}
              disabled={isReconciling || selectedPhotoIds.size === 0}
              style={{
                padding: "10px 20px",
                backgroundColor: isReconciling || selectedPhotoIds.size === 0 ? "#94a3b8" : "#16a34a",
                color: "#ffffff",
                fontWeight: "600",
                fontSize: "0.875rem",
                borderRadius: "8px",
                border: "none",
                cursor: isReconciling || selectedPhotoIds.size === 0 ? "not-allowed" : "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: "8px"
              }}
            >
              {isReconciling
                ? "Đang lưu & cập nhật Snapshot..."
                : `Bổ sung đã chọn (${selectedPhotoIds.size} ảnh)`}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
