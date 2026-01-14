package pl.edu.agh.kis.firebackend.model.simulation;

import lombok.AllArgsConstructor;
import pl.edu.agh.kis.firebackend.model.SectorState;
import pl.edu.agh.kis.firebackend.model.configuration.ConfSector;

import java.util.List;
import java.util.ArrayList;

@AllArgsConstructor
public class Sector 
{
    public int sectorId;
    public SectorState state;
    public List<List<Double>> contours;
    public List<Integer> assignedBrigades;

    public static Sector from(ConfSector confSector) 
    {
        List<Integer> assigned = confSector.assignedBrigades();
        if (assigned == null) assigned = new ArrayList<>();
        return new Sector(confSector.sectorId(), confSector.initialState(), confSector.contours(), assigned);
    }
}
