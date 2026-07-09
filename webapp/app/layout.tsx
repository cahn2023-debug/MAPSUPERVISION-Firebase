import type { Metadata } from "next";
import { IBM_Plex_Mono, Space_Grotesk } from "next/font/google";
import Script from "next/script";
import "maplibre-gl/dist/maplibre-gl.css";
import "./globals.css";

const sans = Space_Grotesk({
  variable: "--font-sans",
  subsets: ["latin", "vietnamese"]
});

const mono = IBM_Plex_Mono({
  variable: "--font-mono",
  subsets: ["latin", "vietnamese"],
  weight: ["400", "500"]
});

export const metadata: Metadata = {
  title: "MapSupervision Sync",
  description: "Web dashboard dong bo du lieu Android qua Firebase"
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="vi" className={`${sans.variable} ${mono.variable}`} suppressHydrationWarning>
      <body suppressHydrationWarning>
        <Script
          id="remove-extension-hydration-attributes"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: `
              document.querySelectorAll("[bis_skin_checked]").forEach(function (element) {
                element.removeAttribute("bis_skin_checked");
              });
            `
          }}
        />
        {children}
      </body>
    </html>
  );
}
