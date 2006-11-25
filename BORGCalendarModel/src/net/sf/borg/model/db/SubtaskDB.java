package net.sf.borg.model.db;

import java.sql.SQLException;
import java.util.Collection;

import net.sf.borg.model.Subtask;
import net.sf.borg.model.Tasklog;


public interface SubtaskDB {

    public Collection getSubTasks(int taskid) throws SQLException;
    
    public Collection getSubTasks() throws SQLException;

    public void deleteSubTask(int id) throws SQLException;

    public void addSubTask(Subtask s) throws SQLException;
    
    public void updateSubTask(Subtask s) throws SQLException;
    
    public int nextSubTaskKey() throws Exception;
    
    public Collection getLogs( int taskid ) throws SQLException;
    public Collection getLogs( ) throws SQLException;
    
    public void addLog(int taskid, String desc) throws SQLException;
    
    public void saveLog( Tasklog tlog ) throws SQLException;

}