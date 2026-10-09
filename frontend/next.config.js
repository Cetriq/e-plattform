/** @type {import('next').NextConfig} */
const nextConfig = {
  // Self-contained server for the Docker image; ignored on Vercel
  output: 'standalone',
  reactStrictMode: true,
};

module.exports = nextConfig;
