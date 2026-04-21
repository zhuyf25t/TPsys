package slaydemo.backend.shared.ports

trait UuidPort {
  def nextId(): String
}
