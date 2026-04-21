package slaydemo.backend.shared.database

final case class ServiceHealthRow(
  serviceName: String,
  status: String
)
